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

import java.util.List;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Stack;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class CloudFormationFacade {

    private AmazonCloudFormation cloudformation;

    public CloudFormationFacade(Regions region, AWSCredentialsProvider credentials) {
        this.cloudformation = AmazonCloudFormationClientBuilder.standard()
                .withCredentials(credentials)
                .withRegion(region)
                .build();
    }

    public boolean isStackInUpdateRollback(String stackId) {
        Stack stack = describeStack(stackId);
        return "UPDATE_ROLLBACK_IN_PROGRESS".equals(stack.getStackStatus());
    }

    private Stack describeStack(String stackId) {
        DescribeStacksRequest request = new DescribeStacksRequest().withStackName(stackId);
        DescribeStacksResult result = cloudformation.describeStacks(request);

        List<Stack> stacks = result.getStacks();
        if (stacks.isEmpty()) {
            String message = String.format("Invalid stackId. No stack found for %s.", stackId);
            throw new RuntimeException(message);
        }
        return stacks.get(0);
    }
}
