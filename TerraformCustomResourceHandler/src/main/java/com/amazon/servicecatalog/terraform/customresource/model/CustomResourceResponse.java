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

import java.util.Map;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Builder
@Value
public class CustomResourceResponse {
    @NonNull private Status status;
    @NonNull private String reason;
    @NonNull private String physicalResourceId;
    @NonNull private String stackId;
    @NonNull private String requestId;
    @NonNull private String logicalResourceId;
    private boolean noEcho;
    private Map<String, String> data;

    public static CustomResourceResponseBuilder builder(CustomResourceRequest request) {
        return new CustomResourceResponseBuilder()
                .stackId(request.getStackId())
                .requestId(request.getRequestId())
                .physicalResourceId(request.getPhysicalResourceId())
                .logicalResourceId(request.getLogicalResourceId());
    }

    public enum Status {
        SUCCESS,
        FAILED
    }
}
