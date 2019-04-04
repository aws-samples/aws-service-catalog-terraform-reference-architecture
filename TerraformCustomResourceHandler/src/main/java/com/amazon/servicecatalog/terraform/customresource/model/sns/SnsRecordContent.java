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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.Value;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SnsRecordContent {
    private String type;
    private String messageId;
    private String topicArn;
    private String subject;
    private String message;
    private String timestamp;
    private String signature;
    private String signatureVersion;
    private String signingCertUrl;
    private String unsubscribeUrl;
    private Map<String, AttributeValue> messageAttributes;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Value
    public static class AttributeValue {
        String type;
        String value;
    }

    /**
     * Classes in the SNS SDK (such as SnsMessageManager) expect a field called "SigningCertURL". This method overrides
     * default getter to use the expected case for the field name during serialization.
     *
     * @return signingCertUrl
     */
    @JsonProperty("SigningCertURL")
    public String getSigningCertUrl() {
        return signingCertUrl;
    }

    /**
     * The JsonProperty of {@link #getSigningCertUrl()} causes Jackson to expect a field called "SigningCertURL" during
     * serialization and deserialization; however, SNS notifications sent to a Lambda will use "SigningCertUrl" as the
     * field name. This method overrides the default setter to use the expected case for the field name during
     * deserialization.
     */
    @JsonProperty("SigningCertUrl")
    public void setSigningCertUrl(String signingCertUrl) {
        this.signingCertUrl = signingCertUrl;
    }

    @JsonProperty("UnsubscribeURL")
    public String getUnsubscribeUrl() {
        return unsubscribeUrl;
    }

    @JsonProperty("UnsubscribeUrl")
    public void setUnsubscribeUrl(String unsubscribeUrl) {
        this.unsubscribeUrl = unsubscribeUrl;
    }
}