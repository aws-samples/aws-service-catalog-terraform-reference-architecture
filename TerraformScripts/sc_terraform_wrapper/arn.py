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

class Arn:
    def __init__(self, arn):
        components = arn.split(':')
        self.arn = arn
        self.partition = components[1]
        self.service = components[2]
        self.region = components[3]
        self.account_id = components[4]

        resource_components = components[5:]
        self.resource_suffix = ':'.join(resource_components)

    def __str__(self):
        return self.arn

