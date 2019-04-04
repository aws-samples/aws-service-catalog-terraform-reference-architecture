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

import java.io.IOException;

import com.amazon.servicecatalog.terraform.customresource.model.TerraformCommandRecord;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class CommandRecordPersistence {

    private static final String S3_KEY_FORMAT = "%s/tf-command-record";
    private static ObjectMapper mapper = new ObjectMapper();

    private AmazonS3 s3;
    private String bucketName;

    public CommandRecordPersistence(String bucketName) {
        this.s3 = AmazonS3ClientBuilder.defaultClient();
        this.bucketName = bucketName;
    }

    public TerraformCommandRecord getCommandRecord(String physicalResourceId) {
        String s3Key = String.format(S3_KEY_FORMAT, physicalResourceId);
        if (!s3.doesObjectExist(bucketName, s3Key)) {
            return null;
        }

        log.info(String.format("Getting Command Record from %s bucket %s path", bucketName, s3Key));
        String record = s3.getObjectAsString(bucketName, s3Key);
        return readCommandRecord(record);
    }

    public void putCommandRecord(String physicalResourceId, String commandId, String instanceId) {
        String s3Key = String.format(S3_KEY_FORMAT, physicalResourceId);
        log.info(String.format("Putting Command Record to %s bucket %s path", bucketName, s3Key));
        TerraformCommandRecord record = TerraformCommandRecord.builder()
                .commandId(commandId)
                .instanceId(instanceId)
                .build();
        s3.putObject(bucketName, s3Key, writeCommandRecord(record));
    }

    private TerraformCommandRecord readCommandRecord(String input) {
        try {
            return mapper.readValue(input, TerraformCommandRecord.class);
        } catch (IOException e) {
            String message = String.format("Exception while serializing SSM command record for %s", input);
            throw new RuntimeException(message);
        }
    }

    private String writeCommandRecord(TerraformCommandRecord record) {
        try {
            return mapper.writeValueAsString(record);
        } catch(JsonProcessingException e) {
            String message = String.format("Exception while deserializing SSM command record for %s", record);
            throw new RuntimeException(message);
        }
    }
}