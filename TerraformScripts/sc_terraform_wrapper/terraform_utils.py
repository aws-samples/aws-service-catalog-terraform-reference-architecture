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

import boto3
import re
import urllib.parse

def get_name_from_cfn_stack_id(stack_arn):
    return stack_arn.resource_suffix.split('/')[1]

def get_s3_location(url):
    # S3 incorrectly encode space as "+". As such, when the user uploads file
    # with name "file name", they would get a URL like
    # "https://s3.amazonaws.com/somebucket/file+name".
    #
    # Turn them into "%20" so that we would decode them correctly later.
    parse_result = urllib.parse.urlparse(url.replace('+', '%20'))
    match = re.match(r'^(.+\.)?s3(?:[-.][^.]+)?\.amazonaws\.com$', parse_result.netloc)
    if not match:
        raise Exception('Hostname does not appear to be a valid S3 endpoint')

    bucket = match.group(1)[:-1] if match.group(1) else None  # remove trailing dot
    path = parse_result.path.lstrip('/')
    if bucket:
        key = path
    else:
        components = path.split('/', maxsplit=1)
        if len(components) != 2:
            raise Exception('S3 URL does not contain object path')
        bucket, key = components
    return (urllib.parse.unquote(bucket), urllib.parse.unquote(key))

def get_assume_role_client(assume_role_input, client_name, region):
    client = boto3.client('sts')
    response = client.assume_role(
        RoleArn=assume_role_input.role_arn,
        RoleSessionName=assume_role_input.session_name,
        ExternalId=assume_role_input.external_id)
    session = boto3.Session(
        aws_access_key_id=response['Credentials']['AccessKeyId'],
        aws_secret_access_key=response['Credentials']['SecretAccessKey'],
        aws_session_token=response['Credentials']['SessionToken'])

    return session.client(client_name, region_name=region)
