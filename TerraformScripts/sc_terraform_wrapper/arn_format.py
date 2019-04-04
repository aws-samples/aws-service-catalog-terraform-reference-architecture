# Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"). You
# may not use this file except in compliance with the License. A copy of
# the License is located at
#
# http://aws.amazon.com/apache2.0/
#
# or in the "license" file accompanying this file. This file is
# distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
# ANY KIND, either express or implied. See the License for the specific
# language governing permissions and limitations under the License.

class ArnFormat():
    PREFIX_FORMAT = "arn:{partition}:{service}:{region}:{account_id}:"

    def __init__(self, service, suffix_format, suffix_attributes, regionless=False, accountless=False):
        self.service = service
        self.suffix_format  = suffix_format
        self.suffix_attributes = suffix_attributes
        self.regionless = regionless
        self.accountless = accountless

    def get_arn(self, region, partition, account_id, suffix_attribute_values):
        arn_region = '' if self.regionless else region
        arn_account_id = '' if self.accountless else account_id

        prefix = self.PREFIX_FORMAT.format(
            region=arn_region,
            partition=partition,
            service=self.service,
            account_id=arn_account_id)
        suffix = self.suffix_format.format(suffix_attribute_values)

        return prefix + suffix