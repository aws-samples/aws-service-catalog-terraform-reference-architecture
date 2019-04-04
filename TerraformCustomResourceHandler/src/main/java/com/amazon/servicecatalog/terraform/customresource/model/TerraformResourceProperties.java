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

package com.amazon.servicecatalog.terraform.customresource.model;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;

import lombok.Value;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Value
public class TerraformResourceProperties {

    private static final String ROLE_ARN_REGEX = "arn:aws:iam::\\d{12}:role/[\\w+=,.@-]{1,64}";

    private String serviceToken;
    private String terraformArtifactUrl;
    private String launchRoleArn;
    private String dryRunId;
    private Map<String, Object> terraformVariables;

    public void validateFields() {
        requireField(serviceToken, "ServiceToken");
        requireField(terraformArtifactUrl, "TerraformArtifactUrl");
        requireField(launchRoleArn, "LaunchRoleArn");

        if (!launchRoleArn.matches(ROLE_ARN_REGEX)) {
            throw new RuntimeException(String.format("LaunchRoleArn %s does not match regex %s", launchRoleArn, ROLE_ARN_REGEX));
        }

        if (terraformVariables != null) {
            List<String> invalidVariables = terraformVariables.entrySet().stream()
                    .filter(e -> !(e.getValue() instanceof String || isListOfString(e.getValue())))
                    .map(Map.Entry::getKey)
                    .collect(ImmutableList.toImmutableList());
            if (!invalidVariables.isEmpty()) {
                throw new RuntimeException(String.format("Invalid Terraform variables %s. Must be string or list of strings", invalidVariables));
            }
        }
    }

    private void requireField(String field, String fieldName) {
        if (field == null) {
            throw new RuntimeException(String.format("Field %s is required", fieldName));
        }
    }

    private static boolean isListOfString(Object o) {
        return o instanceof List && ((List) o).stream().allMatch(v -> v instanceof String);
    }
}
