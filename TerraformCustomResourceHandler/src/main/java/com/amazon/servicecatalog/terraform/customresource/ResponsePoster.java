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

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import com.amazon.servicecatalog.terraform.customresource.model.CustomResourceRequest;
import com.amazon.servicecatalog.terraform.customresource.model.CustomResourceResponse;
import com.amazon.servicecatalog.terraform.customresource.model.CustomResourceResponse.Status;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ResponsePoster {

    private static final HttpClient httpClient = HttpClientBuilder.create().build();

    public static void postSuccess(CustomResourceRequest request) {
        CustomResourceResponse response = CustomResourceResponse.builder(request)
                .status(Status.SUCCESS)
                .reason("") // CFN doesn't post reason for successful events
                .build();
        postResponse(request.getResponseUrl(), response);
    }

    public static void postFailure(CustomResourceRequest request,
                            String reason) {
        CustomResourceResponse response = CustomResourceResponse.builder(request)
                .status(Status.FAILED)
                .reason(reason)
                .build();
        postResponse(request.getResponseUrl(), response);
    }

    private static void postResponse(String responseUrl,
                                     CustomResourceResponse response) {
        try {
            HttpPut putRequest = new HttpPut(responseUrl);
            // Need to suppress Content-Type or S3 would give a 403 invalid signature response.
            putRequest.setHeader("Content-Type", null);
            String serializedResponse = CustomResourceMarshaller.write(response);
            log.info("Posting response: " + serializedResponse);
            putRequest.setEntity(new StringEntity(serializedResponse));

            HttpResponse httpResponse = httpClient.execute(putRequest);

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new RuntimeException(String.format(
                        "Received status code %d when posting response at URL %s to CloudFormation. Entire message: %s.",
                        statusCode, responseUrl, httpResponse));
            }
        } catch (IOException e) {
            String message = "Unable to post response to URL " + responseUrl + " to CloudFormation";
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }
}
