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

package com.amazon.servicecatalog.terraform.customresource.model.sns;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Value;

@JsonIgnoreProperties(ignoreUnknown = true)
@Value
public class SnsNotification {
    List<SnsRecord> records;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Value
    public static class SnsRecord {
        private String eventSource;
        private String eventVersion;
        private String eventSubscriptionArn;
        private SnsRecordContent sns;
    }
}
