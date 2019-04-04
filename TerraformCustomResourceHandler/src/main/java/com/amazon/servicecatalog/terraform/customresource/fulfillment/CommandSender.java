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

package com.amazon.servicecatalog.terraform.customresource.fulfillment;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.amazon.servicecatalog.terraform.customresource.CustomResourceMarshaller;
import com.amazon.servicecatalog.terraform.customresource.ResponsePoster;
import com.amazon.servicecatalog.terraform.customresource.facades.CommandRecordPersistence;
import com.amazon.servicecatalog.terraform.customresource.facades.Ec2Facade;
import com.amazon.servicecatalog.terraform.customresource.facades.SsmFacade;
import com.amazon.servicecatalog.terraform.customresource.model.CustomResourceRequest;
import com.amazon.servicecatalog.terraform.customresource.model.TerraformCommandRecord;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.simplesystemsmanagement.model.GetCommandInvocationResult;
import com.amazonaws.services.simplesystemsmanagement.model.InvocationDoesNotExistException;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class CommandSender {
    private static final String TERRAFORM_COMMAND = "sc-terraform-wrapper '%s' '%s' '%s' '%s' '%s'";
    private static final List<String> EXECUTING_COMMAND_STATUS = ImmutableList.of("Pending", "Delayed", "Cancelling", "InProgress");

    private final SsmFacade ssmFacade;
    private final Ec2Facade ec2Facade;
    private final CommandRecordPersistence commandRecordPersistence;
    private final CustomResourceRequest request;
    private final String externalId;
    private final EnvConfig envConfig;

    public CommandSender(CustomResourceRequest request,
            EnvConfig envConfig,
            String externalId) {
        this.request = request;
        this.externalId = externalId;
        this.envConfig = envConfig;
        this.ec2Facade = new Ec2Facade();
        this.ssmFacade = new SsmFacade();
        this.commandRecordPersistence = new CommandRecordPersistence(envConfig.getCommandRecordS3Bucket());
    }

    public void sendCommand() {
        // verify command status to avoid concurrent updates
        verifyPreviousCommandCompletion();

        Tag instanceTag = envConfig.getInstanceTag();
        String instanceId = ec2Facade.getInstanceId(instanceTag);

        String outputBucket = envConfig.getCommandOutputS3Bucket();
        String outputS3KeyPrefix = createOutputS3KeyPrefix();
        String wrapperScriptOutputS3Key = outputS3KeyPrefix + "/tf_wrapper_script_output";
        String wrapperScriptErrorS3Key = outputS3KeyPrefix + "/tf_wrapper_script_errors";

        String terraformCommand = String.format(
                TERRAFORM_COMMAND,
                CustomResourceMarshaller.write(request),
                outputBucket,
                wrapperScriptOutputS3Key,
                wrapperScriptErrorS3Key,
                externalId);

        List<String> commands = ImmutableList.of(
                "#!/bin/bash",
                "set -o pipefail",
                "tmp_out=/tmp/" + UUID.randomUUID(),
                "tmp_err=/tmp/" + UUID.randomUUID(),
                terraformCommand + " > >(tee $tmp_out) 2> >(tee $tmp_err >&2)",
                "status=$?",
                String.format("aws s3 mv $tmp_out s3://%s/%s", outputBucket, wrapperScriptOutputS3Key),
                String.format("aws s3 mv $tmp_err s3://%s/%s", outputBucket, wrapperScriptErrorS3Key),
                "exit $status"
        );

        // Fire-and-forget send command.
        String ssmOutputS3KeyPrefix = outputS3KeyPrefix + "/ssm_output";
        String commandId = ssmFacade.sendCommand(commands, instanceId, outputBucket, ssmOutputS3KeyPrefix)
                                    .getCommand()
                                    .getCommandId();
        log.info("Sent commandId: " + commandId);
        commandRecordPersistence.putCommandRecord(request.getPhysicalResourceId(), commandId, instanceId);
        bestEffortCheckWhetherCommandIsNotFound(commandId, instanceId);
    }

    private String createOutputS3KeyPrefix() {
        List<String> stackIdParts = Splitter.on(':').splitToList(request.getStackId());
        String region = stackIdParts.get(3);
        String accountId = stackIdParts.get(4);
        String stackName = Splitter.on('/').splitToList(stackIdParts.get(5)).get(1);
        return String.format("%s/%s/%s/%s-%s", accountId, region, stackName, System.currentTimeMillis(), request.getRequestType());
    }

    private void bestEffortCheckWhetherCommandIsNotFound(String commandId, String instanceId) {
        try {
            TimeUnit.SECONDS.sleep(30);
        } catch (InterruptedException e) {
            log.warn("Sleep before getting SSM command interrupted.");
        }

        try {
            GetCommandInvocationResult commandResult = ssmFacade.getCommand(commandId, instanceId);
            if ("FAILED".equals(commandResult.getStatus()) && commandResult.getResponseCode() == 127) {
                String message = String.format("Terraform wrapper script not found at %s on instance %s. SSM command ID: %s",
                                               TERRAFORM_COMMAND, instanceId, commandId);
                ResponsePoster.postFailure(request, message);
            }
        } catch (RuntimeException e) {
            log.warn("Encountered exception while trying to determine whether command is not found on Terraform server.", e);
        }
    }

    private void verifyPreviousCommandCompletion() {
        TerraformCommandRecord record = commandRecordPersistence.getCommandRecord(request.getPhysicalResourceId());
        // no concurrent command exists
        if (record == null) {
            return;
        }

        String commandId = record.getCommandId();
        String instanceId = record.getInstanceId();

        try {
            GetCommandInvocationResult commandResult = ssmFacade.getCommand(commandId, instanceId);

            if (EXECUTING_COMMAND_STATUS.contains(commandResult.getStatus())) {
                String message = String.format("SSM is still executing a Terraform command for this stack. Command " +
                                "ID: %s. Instance Id: %s.", commandId, instanceId);
                ResponsePoster.postFailure(request, message);
            }
        } catch (InvocationDoesNotExistException e) {
            String message = String.format("A command record was found, but no invocation exists for InstanceId ID " +
                    "%s with CommandId %s. Beginning command execution with the assumption that the previous command " +
                    "has completed and expired.", instanceId, commandId);
            log.warn(message);
        }
    }
}
