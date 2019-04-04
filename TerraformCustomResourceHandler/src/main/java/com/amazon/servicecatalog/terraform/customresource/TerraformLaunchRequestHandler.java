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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.amazon.servicecatalog.terraform.customresource.fulfillment.EnvConfig;
import com.amazon.servicecatalog.terraform.customresource.model.CustomResourceRequest;
import com.amazon.servicecatalog.terraform.customresource.util.ArnParser;
import com.amazonaws.Response;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.AmazonSNSException;
import com.amazonaws.services.sns.model.AuthorizationErrorException;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TerraformLaunchRequestHandler implements RequestStreamHandler {

    private static final String HUB_SNS_ARN_ENV_VAR = "HUB_SNS_TOPIC_ARN";
    public static final String ACCOUNT_ID_ATTRIBUTE_KEY = "AccountId";

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) {
        MessageAttributeValue accountIdValue = new MessageAttributeValue()
                .withDataType("String")
                .withStringValue(getAccountId(context));
        Map<String, MessageAttributeValue> messageAttributes =
                ImmutableMap.of(ACCOUNT_ID_ATTRIBUTE_KEY, accountIdValue);

        String hubSnsTopicArn = EnvConfig.getRequiredEnv(HUB_SNS_ARN_ENV_VAR);
        String cfnRequest = toRequestString(inputStream);

        try {
            publishNotification(hubSnsTopicArn, cfnRequest, messageAttributes);
        } catch (RuntimeException e) {
            CustomResourceRequest request = CustomResourceMarshaller.readCustomResourceRequest(cfnRequest, true);
            String message = String.format("Unable to publish SNS notification to hub account SNS topic. %s",
                                            e.getMessage());
            ResponsePoster.postFailure(request, message);
        }
    }

    private String getAccountId(Context context) {
        String lambdaArn = context.getInvokedFunctionArn();
        return ArnParser.getAccountId(lambdaArn);
    }

    private String toRequestString(InputStream inputStream) {
        try {
            return CharStreams.toString(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void publishNotification(String hubSnsTopicArn, String cfnRequest,
                                     Map<String, MessageAttributeValue> messageAttributes) {
        String region = ArnParser.getRegion(hubSnsTopicArn);
        AmazonSNS sns = AmazonSNSClientBuilder.standard()
                .withRegion(region)
                .build();
        sns.publish(new PublishRequest()
                .withTopicArn(hubSnsTopicArn)
                .withMessage(cfnRequest)
                .withSubject("AWS CloudFormation custom resource request with requester AccountId")
                .withMessageAttributes(messageAttributes)
        );
    }
}
