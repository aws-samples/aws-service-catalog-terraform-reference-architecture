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

import static java.util.Objects.requireNonNull;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@ToString
@EqualsAndHashCode
public class CustomResourceRequest {

    private final String serviceToken;
    private final RequestType requestType;
    @JsonProperty("ResponseURL") private final String responseUrl;
    private final String stackId;
    private final String requestId;
    private final String resourceType;
    private final String logicalResourceId;
    private final String physicalResourceId;
    private final TerraformResourceProperties resourceProperties;
    private final TerraformResourceProperties oldResourceProperties;

    @JsonCreator
    @Builder(toBuilder = true)
    public CustomResourceRequest(@JsonProperty("ServiceToken") String serviceToken,
            @JsonProperty("RequestType") RequestType requestType,
            @JsonProperty("ResponseURL") String responseUrl,
            @JsonProperty("StackId") String stackId,
            @JsonProperty("RequestId") String requestId,
            @JsonProperty("ResourceType") String resourceType,
            @JsonProperty("LogicalResourceId") String logicalResourceId,
            @JsonProperty("PhysicalResourceId") String physicalResourceId,
            @JsonProperty("ResourceProperties") TerraformResourceProperties resourceProperties,
            @JsonProperty("OldResourceProperties") TerraformResourceProperties oldResourceProperties) {

        this.serviceToken = requireNonNull(serviceToken);
        this.requestType = requireNonNull(requestType);
        this.responseUrl = requireNonNull(responseUrl);
        this.stackId = requireNonNull(stackId);
        this.requestId = requireNonNull(requestId);
        this.resourceType = requireNonNull(resourceType);
        this.logicalResourceId = requireNonNull(logicalResourceId);

        if (physicalResourceId != null) {
            this.physicalResourceId = physicalResourceId;
        } else {
            String relativeId = Iterables.getLast(Splitter.on(':').split(stackId));
            List<String> relativeIdParts = Splitter.on('/').splitToList(relativeId);
            // stackName-logicalResourceId-UUID
            this.physicalResourceId = String.format("%s-%s-%s", relativeIdParts.get(1), logicalResourceId, relativeIdParts.get(2));
        }

        this.resourceProperties = requireNonNull(resourceProperties);
        this.oldResourceProperties = oldResourceProperties;
    }
}
