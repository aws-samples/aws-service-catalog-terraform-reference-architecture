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
import random
import sc_terraform_wrapper.terraform_utils as terraform_utils
import time

TAG_RETRY_TIMES = 3
RETRY_DELAY_CAP = 5
RETRY_DELAY_BASE = 1

def tag_resources_with_retry(client, arns, tags, nth_try):
    print('Tagging try #{}. Attempt to tag ARNs: {}'.format(nth_try, arns))

    failed_arns = []
    ignored_arns = []

    for arn in arns:
        error_code = None
        try:
            response = client.tag_resources(
                ResourceARNList=[arn],
                Tags=tags
            )
            if response.get('FailedResourcesMap'):
                error_code = next(iter(response['FailedResourcesMap'].values()))['ErrorCode']
        except ClientError as e:
            error_code = e.response['Error']['Code']

        if error_code:
            if error_code == 'InvalidParameterException':
                ignored_arns.append(arn)
            else:
                failed_arns.append(arn)

    if ignored_arns:
        print('Some ARNs are invalid for tagging. Ignoring: ' + ''.join(ignored_arns))

    # retry failed resources with backoff
    if failed_arns:
        if nth_try <= TAG_RETRY_TIMES:
            # Exponential backoff with full jitter. Reference:
            # https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
            temp = min(RETRY_DELAY_CAP, RETRY_DELAY_BASE * 2 ** nth_try)
            delay = temp / 2 + random.uniform(0, temp / 2)
            time.sleep(delay)
            print('Retry tagging #{}'.format(nth_try))
            return tag_resources_with_retry(client, failed_arns, tags, nth_try + 1)
        else:
            print('Tagging failed on {}'.format(failed_arns))

def tag_resources(arns, tags, region, assume_role_input):
    resource_groups_tagging = terraform_utils.get_assume_role_client(assume_role_input, 'resourcegroupstaggingapi', region)
    tag_resources_with_retry(resource_groups_tagging, arns, tags, 1)

def retrieve_user_tags_from_cfn(stack_arn, assume_role_input):
    tags = _retrieve_tags_from_cfn(stack_arn, assume_role_input)
    return { tag_key : tag_value for tag_key, tag_value in tags.items() if not tag_key.startswith('aws:') }

def _retrieve_tags_from_cfn(stack_arn, assume_role_input):
    cfn_client = terraform_utils.get_assume_role_client(assume_role_input, 'cloudformation', stack_arn.region)
    describe_stacks_response = cfn_client.describe_stacks(StackName=str(stack_arn))
    stack = describe_stacks_response['Stacks'][0]
    return { t['Key']: t['Value'] for t in stack.get('Tags', []) }