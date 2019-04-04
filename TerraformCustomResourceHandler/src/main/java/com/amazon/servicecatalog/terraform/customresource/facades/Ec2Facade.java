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
import java.util.Random;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.collect.ImmutableList;

public class Ec2Facade {

    private static final Filter RUNNING_INSTANCE_FILTER = new Filter("instance-state-name", ImmutableList.of("running"));
    private static final Random randomGenerator = new Random();

    private AmazonEC2 ec2;

    public Ec2Facade() {
        this.ec2 = AmazonEC2ClientBuilder.defaultClient();
    }

    public String getInstanceId(Tag instanceTag) {
        Filter tagFilter = new Filter("tag:" + instanceTag.getKey(), ImmutableList.of(instanceTag.getValue()));
        DescribeInstancesRequest request = new DescribeInstancesRequest().withFilters(tagFilter, RUNNING_INSTANCE_FILTER);
        DescribeInstancesResult result = ec2.describeInstances(request);

        List<String> instanceIds;
        if (result.getReservations() != null) {
            instanceIds = result.getReservations().stream()
                    .flatMap(reservation -> reservation.getInstances().stream())
                    .map(Instance::getInstanceId)
                    .collect(ImmutableList.toImmutableList());
        } else {
            instanceIds = ImmutableList.of();
        }

        if (instanceIds.isEmpty()) {
            String message = String.format(
                    "Invalid FulfillmentConfig. No instances found with TagKey: %s and TagValue: %s",
                    instanceTag.getKey(),
                    instanceTag.getValue());
            throw new RuntimeException(message);
        }

        int randomIndex = randomGenerator.nextInt(instanceIds.size());
        return instanceIds.get(randomIndex);
    }
}
