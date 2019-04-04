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

package com.amazon.servicecatalog.terraform.customresource.util;

import com.google.common.base.Splitter;

public final class ArnParser {
    private static final Splitter DELIMITER_SPLITTER = Splitter.on(':');

    private ArnParser() {}

    public static String getRegion(String arn) {
        return DELIMITER_SPLITTER.splitToList(arn).get(3);
    }

    public static String getAccountId(String arn) {
        return DELIMITER_SPLITTER.splitToList(arn).get(4);
    }

    public static String getRelativeId(String arn) {
        return DELIMITER_SPLITTER.splitToList(arn).get(5);
    }
}
