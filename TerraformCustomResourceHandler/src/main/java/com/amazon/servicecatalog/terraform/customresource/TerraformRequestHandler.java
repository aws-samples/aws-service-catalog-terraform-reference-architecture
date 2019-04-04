/*
 * Copyright 2014-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.servicecatalog.terraform.customresource;

import static com.amazon.servicecatalog.terraform.customresource.TerraformLaunchRequestHandler.ACCOUNT_ID_ATTRIBUTE_KEY;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.amazon.servicecatalog.terraform.customresource.facades.CloudFormationFacade;
import com.amazon.servicecatalog.terraform.customresource.facades.StsFacade;
import com.amazon.servicecatalog.terraform.customresource.fulfillment.CommandSender;
import com.amazon.servicecatalog.terraform.customresource.fulfillment.EnvConfig;
import com.amazon.servicecatalog.terraform.customresource.model.CustomResourceRequest;
import com.amazon.servicecatalog.terraform.customresource.model.RequestType;
import com.amazon.servicecatalog.terraform.customresource.model.TerraformResourceProperties;
import com.amazon.servicecatalog.terraform.customresource.model.sns.SnsRecordContent;
import com.amazon.servicecatalog.terraform.customresource.util.ArnParser;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.s3.AmazonS3URI;
import com.google.common.base.Splitter;
import com.google.common.io.CharStreams;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TerraformRequestHandler implements RequestStreamHandler {

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) {
        String requestString = toRequestString(inputStream);
        log.trace("Original unparsed input:\n" + requestString);

        SnsRecordContent recordContent;
        CustomResourceRequest request;
        try {
            recordContent = CustomResourceMarshaller.readSnsRecordContent(requestString, false);
            request = CustomResourceMarshaller.readCustomResourceRequest(recordContent, false);
        } catch (RuntimeException e) {
            log.error("Failed to parse request.", e);
            try {
                recordContent = CustomResourceMarshaller.readSnsRecordContent(requestString, true);
                request = CustomResourceMarshaller.readCustomResourceRequest(recordContent, true);
                ResponsePoster.postFailure(request, "Failed to parse request: " + e.getMessage());
            } catch (RuntimeException ex) {
                log.error("Unexpected error parsing request or posting failure response.", ex);
            }
            return;
        }

        try {
            // Best-effort validation so we don't send unnecessary commands to SSM
            TerraformResourceProperties properties = request.getResourceProperties();
            properties.validateFields();
            verifyNoCrossAccountAccess(properties, recordContent);
            CustomResourceMarshaller.verifySnsSignature(recordContent);
            handle(context, request);
        } catch (RuntimeException e) {
            ResponsePoster.postFailure(request, e.getMessage());
            log.error("Unexpected error encountered when handling the request.", e);
        }
    }

    private void handle(Context context, CustomResourceRequest request) {
        EnvConfig envConfig = EnvConfig.fromEnvironmentVariables();
        String externalId = StsFacade.getExternalId(context);
        AWSCredentialsProvider launchRoleCredentials = getLaunchRoleCredentials(externalId, request);

        // Since Terraform doesn't handle rollback, we no-op for rollback cases. Simply post success.
        CloudFormationFacade cfnFacade = getCfnFacade(request, launchRoleCredentials);
        if (request.getRequestType() == RequestType.UPDATE && cfnFacade.isStackInUpdateRollback(request.getStackId())) {
            ResponsePoster.postSuccess(request);
            return;
        }

        verifyWhitelistedTerraformArtifactSource(request.getResourceProperties(), envConfig);
        CommandSender commandSender = new CommandSender(request, envConfig, externalId);
        commandSender.sendCommand();
    }

    private String toRequestString(InputStream inputStream) {
        try {
            return CharStreams.toString(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static AWSCredentialsProvider getLaunchRoleCredentials(String externalId, CustomResourceRequest request) {
        String launchRoleArn = request.getResourceProperties().getLaunchRoleArn();
        return new StsFacade().getCredentialsProvider(launchRoleArn, externalId);
    }

    private static CloudFormationFacade getCfnFacade(CustomResourceRequest request, AWSCredentialsProvider launchRoleCredentials) {
        String stackId = request.getStackId();
        Regions stackRegion = Regions.fromName(Splitter.on(':').splitToList(stackId).get(3));
        return new CloudFormationFacade(stackRegion, launchRoleCredentials);
    }

    /**
     * Verify that the S3 bucket of the TerraformArtifactUrl is whitelisted
     *
     * @param properties the custom resource request properties
     * @param envConfig the environment configurations of the Lambda
     */
    private void verifyWhitelistedTerraformArtifactSource(TerraformResourceProperties properties, EnvConfig envConfig) {
        AmazonS3URI s3URI;
        try {
            s3URI = new AmazonS3URI(properties.getTerraformArtifactUrl());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid TerraformArtifactUrl. " + e.getMessage());
        }

        String terraformArtifactBucket = s3URI.getBucket();
        String whitelistedBucket = envConfig.getTerraformArtifactS3Bucket();
        if (!whitelistedBucket.equals(terraformArtifactBucket)) {
            String message = String.format("Invalid TerraformArtifactUrl. TerraformArtifacts must be contained in the" +
                    " following bucket: %s", whitelistedBucket);
            throw new RuntimeException(message);
        }
    }

    /**
     * Prevent cross-account access by verifying that the account of the user making the request (identified by the
     * AccountId SNS attribute) matches the account where the resources will be created (identified the the account
     * of the LaunchRoleARN).
     *
     * @param properties the custom resource request properties of the SNS notification
     * @param recordContent the record content of the SNS notification
     */
    private void verifyNoCrossAccountAccess(TerraformResourceProperties properties, SnsRecordContent recordContent) {
        String launchRoleArn = properties.getLaunchRoleArn();
        String launchRoleAccountId = ArnParser.getAccountId(launchRoleArn);

        String requesterAccountId = Optional.ofNullable(recordContent.getMessageAttributes())
                .map(attributes -> attributes.get(ACCOUNT_ID_ATTRIBUTE_KEY))
                .map(SnsRecordContent.AttributeValue::getValue)
                .orElseThrow(() -> new RuntimeException("SNS input message does not contain AccountId attribute"));

        if (!requesterAccountId.equals(launchRoleAccountId)) {
            throw new RuntimeException("To prevent permissions escalation TerraformStacks cannot use a LaunchRoleArn " +
                    "that references another account.");
        }
    }
}
