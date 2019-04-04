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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.amazon.servicecatalog.terraform.customresource.model.CustomResourceRequest;
import com.amazon.servicecatalog.terraform.customresource.model.CustomResourceResponse;
import com.amazon.servicecatalog.terraform.customresource.model.sns.SnsNotification;
import com.amazon.servicecatalog.terraform.customresource.model.sns.SnsNotification.SnsRecord;
import com.amazon.servicecatalog.terraform.customresource.model.sns.SnsRecordContent;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.sns.message.SnsMessageManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class CustomResourceMarshaller {

    private static ObjectMapper strictMapper;
    private static ObjectMapper lenientMapper;
    private static SnsMessageManager messageManager = new SnsMessageManager();

    static {
        strictMapper = new ObjectMapper();
        strictMapper.setPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE);

        lenientMapper = new ObjectMapper();
        lenientMapper.setPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE);
        lenientMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static SnsRecordContent readSnsRecordContent(String input, boolean readLeniently) {
        try {
            ObjectMapper mapper = readLeniently ? lenientMapper : strictMapper;
            SnsNotification notification = mapper.readValue(input, SnsNotification.class);
            return getRecordContentFromSnsNotification(notification);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static CustomResourceRequest readCustomResourceRequest(SnsRecordContent recordContent, boolean readLeniently) {
        String message = Optional.ofNullable(recordContent.getMessage())
                .orElseThrow(() -> new RuntimeException("Unexpected SNS input message format."));
        return readCustomResourceRequest(message, readLeniently);
    }

    public static CustomResourceRequest readCustomResourceRequest(String request, boolean readLeniently) {
        try {
            ObjectMapper mapper = readLeniently ? lenientMapper : strictMapper;
            String message = Optional.ofNullable(request)
                    .orElseThrow(() -> new RuntimeException("Unexpected SNS input message format."));
            return mapper.readValue(message, CustomResourceRequest.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void verifySnsSignature(SnsRecordContent notification) {
        String notificationString;
        try {
            notificationString = lenientMapper.writeValueAsString(notification);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        byte[] notificationBytes = notificationString.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream notificationInputStream = new ByteArrayInputStream(notificationBytes);

        try {
            messageManager.parseMessage(notificationInputStream);
        } catch (SdkClientException e) {
            throw new RuntimeException("Unable to verify SNS notification signature.", e);
        }
    }

    private static SnsRecordContent getRecordContentFromSnsNotification(SnsNotification snsNotification) {
        return Optional.ofNullable(snsNotification.getRecords())
                .filter(rs -> !rs.isEmpty())
                .map(rs -> rs.get(0))
                .map(SnsRecord::getSns)
                .orElseThrow(() -> new RuntimeException("Unexpected SNS input message format."));
    }

    public static String write(CustomResourceRequest request) {
        try {
            return strictMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String write(CustomResourceResponse response) {
        try {
            return strictMapper.writeValueAsString(response);
        } catch(JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
