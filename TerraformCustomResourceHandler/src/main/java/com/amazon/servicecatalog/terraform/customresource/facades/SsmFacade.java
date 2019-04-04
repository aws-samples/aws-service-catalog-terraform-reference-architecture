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

package com.amazon.servicecatalog.terraform.customresource.facades;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetCommandInvocationRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetCommandInvocationResult;
import com.amazonaws.services.simplesystemsmanagement.model.InvalidInstanceIdException;
import com.amazonaws.services.simplesystemsmanagement.model.SendCommandRequest;
import com.amazonaws.services.simplesystemsmanagement.model.SendCommandResult;
import com.amazonaws.services.simplesystemsmanagement.model.UnsupportedPlatformTypeException;
import com.google.common.collect.ImmutableMap;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class SsmFacade {
    private static final String RUN_SCRIPT_SSM_DOCUMENT = "AWS-RunShellScript";
    private static final String COMMAND_PARAMETER_NAME = "commands";
    private static final String WORKING_DIRECTORY_PARAMETER_NAME = "workingDirectory";
    private static final String DEFAULT_HOME_DIRECTORY = "/home/ec2-user";

    private AWSSimpleSystemsManagement ssm;

    public SsmFacade() {
        this.ssm = AWSSimpleSystemsManagementClientBuilder.defaultClient();
    }

    public SendCommandResult sendCommand(List<String> commands,
            String instanceId,
            String outputS3Bucket,
            String outputS3KeyPrefix) {
        Map<String, List<String>> parameters = ImmutableMap.of(
                COMMAND_PARAMETER_NAME, commands,
                WORKING_DIRECTORY_PARAMETER_NAME, Collections.singletonList(DEFAULT_HOME_DIRECTORY)
        );

        SendCommandRequest commandRequest = new SendCommandRequest()
                .withInstanceIds(instanceId)
                .withParameters(parameters)
                .withOutputS3BucketName(outputS3Bucket)
                .withOutputS3KeyPrefix(outputS3KeyPrefix)
                .withDocumentName(RUN_SCRIPT_SSM_DOCUMENT);

        try {
            return ssm.sendCommand(commandRequest);
        } catch (InvalidInstanceIdException e) {
            String message = String.format("Received InvalidInstanceId Error from AWS Systems Manager when sending " +
                    "a command to the FulfillmentServer, %s. Verify that the instance is configured correctly.",
                    instanceId);
            throw new RuntimeException(message);
        } catch (UnsupportedPlatformTypeException e) {
            String message = String.format("ServiceCatalog does not support the platform type of the " +
                    "FulfillmentServer, %s.", instanceId);
            throw new RuntimeException(message);
        }
    }

    public GetCommandInvocationResult getCommand(String commandId, String instanceId) {
        GetCommandInvocationRequest request = new GetCommandInvocationRequest()
                .withInstanceId(instanceId)
                .withCommandId(commandId);
        return ssm.getCommandInvocation(request);
    }
}
