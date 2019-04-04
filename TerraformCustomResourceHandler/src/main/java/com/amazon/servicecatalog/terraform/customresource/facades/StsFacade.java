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

import java.util.UUID;

import com.amazon.servicecatalog.terraform.customresource.util.ArnParser;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class StsFacade {
    private static final String EXTERNAL_ID_FORMAT= "TerraformHubAccount-%s";
    private AWSSecurityTokenService sts;

    public StsFacade() {
        this.sts = AWSSecurityTokenServiceClientBuilder.defaultClient();
    }

    public static String getExternalId(Context context) {
        String functionArn = context.getInvokedFunctionArn();
        String accountId = ArnParser.getAccountId(functionArn);
        return String.format(EXTERNAL_ID_FORMAT, accountId);
    }

    /**
     * Generate an AWSCredentialsProvider for the roleArn
     *
     * @param roleArn the role that will be assumed
     * @param externalId the externalId to use when assuming the role
     * @return an AWSCredentialsProvider for the roleArn
     */
    public AWSCredentialsProvider getCredentialsProvider(String roleArn, String externalId) {
        return new STSAssumeRoleSessionCredentialsProvider.Builder(roleArn, UUID.randomUUID().toString())
                .withStsClient(sts)
                .withExternalId(externalId)
                .build();
    }
}
