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

from botocore.exceptions import ClientError
from hashlib import sha256
from sc_terraform_wrapper.arn import Arn
import hashlib
import json
import sc_terraform_wrapper.terraform_utils as terraform_utils

def build_query_filter(resource_group_name):
    return {
        'ResourceTypeFilters': ['AWS::AllSupported'],
        'TagFilters': [ {'Key': 'TfResourceGroupName', 'Values': [resource_group_name]} ]
    }

def create_resource_group_if_not_exist(tags, assume_role_input):
    client = terraform_utils.get_assume_role_client(assume_role_input, 'resource-groups', Arn(tags['CfnStackId']).region)

    resource_group_name = tags['TfResourceGroupName']
    if not does_group_exist(client, resource_group_name):
        response = client.create_group(
            Name=resource_group_name,
            Description='Auto-created from Terraform wrapper script',
            ResourceQuery={
                'Type': 'TAG_FILTERS_1_0',
                'Query': json.dumps(build_query_filter(resource_group_name))
            },
            Tags=tags
        )
        print('Created resource group: {}'.format(response))
    else:
        print('Resource group {} already existed. Nothing to create'.format(resource_group_name))

def does_group_exist(client, group_name):
    try:
        client.get_group(GroupName=group_name)
    except ClientError as e:
        if e.response['Error']['Code'] == 'NotFoundException':
            return False
        else:
            raise e

    return True

def delete_resource_group(resource_group_name, region, assume_role_input):
    resource_groups = terraform_utils.get_assume_role_client(assume_role_input, 'resource-groups', region)

    try:
        response = resource_groups.delete_group(GroupName=resource_group_name)
        print('Deleted resource group: {}'.format(response))
    except ClientError as e:
        if e.response['Error']['Code'] == 'NotFoundException':
            print('Resource group {} does not exist. Nothing to delete'.format(resource_group_name))
        else:
            raise e

def construct_resource_group_name(request):
    stack_arn = Arn(request['StackId'])
    cfn_stack_name = terraform_utils.get_name_from_cfn_stack_id(stack_arn)
    logical_resource_id = request['LogicalResourceId']
    # Include:
    # - CFN stack name and logical resource ID so it's more easily identifiable
    #   where the resource group comes from.
    # - SHA256 hash of the CFN stack ID and logical resource ID to prevent collision.
    #
    # Also, resource group name limit is 128, so can only include parts of CFN
    # stack name and logical resource ID.
    sha256_hash = sha256((request['StackId'] + '|' + logical_resource_id).encode('utf-8')).hexdigest()
    return '-'.join([cfn_stack_name[:32], logical_resource_id[:30], sha256_hash])
