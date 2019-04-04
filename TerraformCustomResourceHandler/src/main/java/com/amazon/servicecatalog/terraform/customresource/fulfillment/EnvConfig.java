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

import com.amazonaws.services.ec2.model.Tag;

import lombok.NonNull;
import lombok.Value;

@Value
public class EnvConfig {

    private static final String TERRAFORM_SERVER_TAG_KEY_ENV_VAR = "TERRAFORM_SERVER_TAG_KEY";
    private static final String TERRAFORM_SERVER_TAG_VALUE_ENV_VAR = "TERRAFORM_SERVER_TAG_VALUE";
    private static final String COMMAND_OUTPUT_S3_BUCKET_ENV_VAR = "COMMAND_OUTPUT_S3_BUCKET";
    private static final String TERRAFORM_SSM_COMMAND_BUCKET_ENV_VAR = "TERRAFORM_SSM_COMMAND_BUCKET";
    private static final String WHITELISTED_TERRAFORM_ARTIFACT_BUCKET_ENV_VAR = "WHITELISTED_TERRAFORM_ARTIFACT_BUCKET";

    private static final String DEFAULT_TERRAFORM_SERVER_TAG_KEY = "terraform-server-tag-key";
    private static final String DEFAULT_TERRAFORM_SERVER_TAG_VALUE = "terraform-server-tag-value";

    @NonNull private final String commandOutputS3Bucket;
    @NonNull private final String commandRecordS3Bucket;
    @NonNull private final Tag instanceTag;
    @NonNull private final String terraformArtifactS3Bucket;

    public static EnvConfig fromEnvironmentVariables() {
        return new EnvConfig(getRequiredEnv(COMMAND_OUTPUT_S3_BUCKET_ENV_VAR),
                getRequiredEnv(TERRAFORM_SSM_COMMAND_BUCKET_ENV_VAR),
                getInstanceTagFromEnv(),
                getRequiredEnv(WHITELISTED_TERRAFORM_ARTIFACT_BUCKET_ENV_VAR));
    }

    public static String getRequiredEnv(String envVariable) {
        String envValue = System.getenv(envVariable);
        if (envValue == null) {
            throw new RuntimeException("Required environment variable is missing from Lambda: " + envVariable);
        }
        return envValue;
    }

    private static Tag getInstanceTagFromEnv() {
        String serverTagKey = System.getenv(TERRAFORM_SERVER_TAG_KEY_ENV_VAR);
        String serverTagValue = System.getenv(TERRAFORM_SERVER_TAG_VALUE_ENV_VAR);
        return new Tag(serverTagKey != null ? serverTagKey : DEFAULT_TERRAFORM_SERVER_TAG_KEY,
                       serverTagValue != null ? serverTagValue : DEFAULT_TERRAFORM_SERVER_TAG_VALUE);
    }
}
