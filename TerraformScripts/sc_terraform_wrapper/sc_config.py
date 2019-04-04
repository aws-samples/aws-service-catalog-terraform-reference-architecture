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

import json

CONFIG_FILE = '/usr/local/var/sc-config.json'
REQUIRED_CONFIG_FIELDS = [ 'bucket', 'region', 'root-workspace-path' ]

# Load JSON config that specifies the following fields:
#
# bucket - the name of the bucket used as backend for the Terraform execution
# region - the region where all buckets referenced during Terraform execution are located
# root-workspace-path - directory where Terraform execution should be performed in
#
# Example configuration file:
#
# {
#   "bucket": "terraform-scripts-config-store",
#   "region": "us-east-1",
#   "root-workspace-path": "~/terraform/scripts-root-workspace"
# }
def load_config(verbose=False):
    if verbose:
        print('Attempt to load configuration at: ' + CONFIG_FILE)

    config = None
    with open(CONFIG_FILE) as f:
        config = json.load(f)

    missing_fields = [field for field in REQUIRED_CONFIG_FIELDS if field not in config]
    if missing_fields:
        raise Exception('Missing required fields {} in config at location {}'.format(missing_fields, CONFIG_FILE))
    return config
