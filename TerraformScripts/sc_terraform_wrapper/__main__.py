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

from collections import namedtuple
from sc_terraform_wrapper.arn import Arn
from sc_terraform_wrapper.cfn_url_parser import seconds_until_expiry
from sc_terraform_wrapper.response_poster import ResponsePoster
from sc_terraform_wrapper.terraform_executor import TerraformExecutor
import argparse
import boto3
import botocore
import json
import os
import sc_terraform_wrapper.sc_config as sc_config
import sc_terraform_wrapper.terraform_resource_group as terraform_resource_group
import sc_terraform_wrapper.terraform_state as terraform_state
import sc_terraform_wrapper.terraform_tag as terraform_tag
import sc_terraform_wrapper.terraform_utils as terraform_utils
import shutil
import signal
import uuid
import zipfile

BACKEND_CONFIG_FORMAT = """terraform {{
  backend "s3" {{
    bucket = "{bucket}"
    key    = "{key}"
    region = "{region}"
  }}
}}
"""

AWS_PROVIDER_FORMAT = """provider "aws" {{
  assume_role = {{
    role_arn = "{role_arn}"
    session_name = "{session_name}"
    external_id = "{external_id}"
  }}
  region = "{region}"
}}
"""

REQUIRED_REQUEST_FIELDS = (
    'RequestType',
    'ResponseURL',
    'StackId',
    'RequestId',
    'LogicalResourceId',
    'PhysicalResourceId',
    'ResourceProperties'
)

REQUIRED_RESOURCE_FIELDS = ('TerraformArtifactUrl', 'LaunchRoleArn')

AssumeRoleInput = namedtuple('AssumeRoleInput', ['role_arn', 'external_id', 'session_name'])

def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument('request', type=json.loads, help='Request object from CloudFormation')
    parser.add_argument('output_bucket', help="S3 bucket where this script's output will be posted. Only used for posting response to CFN")
    parser.add_argument('output_key', help="S3 object name for the script's output. Only used for posting response to CFN")
    parser.add_argument('error_key', help="S3 object name for the any errors related to the script. Only used for posting response to CFN")
    parser.add_argument('external_id', help="ExternalId that should be used when assuming the ResourceCreationRole in the spoke account")
    return parser.parse_args()

def validate_request_arg(request):
    missing_request_fields = [field for field in REQUIRED_REQUEST_FIELDS if not request.get(field)]
    if missing_request_fields:
        raise Exception('Unexpected error. CloudFormation did not pass required fields to Terraform server: ' + str(missing_request_fields))

    resource_properties = request['ResourceProperties']
    missing_resource_fields = [field for field in REQUIRED_RESOURCE_FIELDS if not resource_properties.get(field)]
    if missing_resource_fields:
        raise Exception('Expected fields from Terraform resource properties are missing: ' + str(missing_resource_fields))

    # Check that S3 URL can be parsed
    terraform_utils.get_s3_location(resource_properties['TerraformArtifactUrl'])

def inject_backend_config(workspace_path, config, physical_resource_id):
    state_file_key = '{name}/terraform.tfstate'.format(name=physical_resource_id)
    backend_config_content = BACKEND_CONFIG_FORMAT.format(key=state_file_key, **config)
    backend_config_file_name = 'backend-config-{}.tf'.format(uuid.uuid4())
    with open(os.path.join(workspace_path, backend_config_file_name), 'w') as f:
        f.write(backend_config_content)

    state_file_location = "s3://{}/{}".format(config['bucket'], state_file_key)
    return state_file_location

def inject_aws_provider_override(workspace_path, region, assume_role_input):
    file_name = '{}-aws_provider_override.tf'.format(uuid.uuid4())
    with open(os.path.join(workspace_path, file_name), 'w') as f:
        f.write(AWS_PROVIDER_FORMAT.format(
            role_arn=assume_role_input.role_arn,
            session_name=assume_role_input.session_name,
            external_id=assume_role_input.external_id,
            region=region))

def inject_variables(workspace_path, resource_properties):
    if resource_properties.get('TerraformVariables'):
        variables_file_name = 'variables-{}.auto.tfvars'.format(uuid.uuid4())
        with open(os.path.join(workspace_path, variables_file_name), 'w') as f:
            json.dump(resource_properties['TerraformVariables'], f)

def get_exception_msg(e):
    type_name = type(e).__name__
    msg = getattr(e, 'message', str(e))
    return '{}: {}'.format(type_name, msg) if type_name != 'Exception' else msg

def download_artifact(s3, artifact_url, artifact_file_local_path, workspace_path, cleanups):
    print('Downloading artifact file')
    artifact_bucket, artifact_key = terraform_utils.get_s3_location(artifact_url)
    try:
        s3.download_file(artifact_bucket, artifact_key, artifact_file_local_path)
    except botocore.exceptions.ClientError as e:
        if e.response['Error']['Code'] == '404':
            raise Exception('Terraform config does not exist. No config found at {}'
                            .format(artifact_url))

    if zipfile.is_zipfile(artifact_file_local_path):
        with zipfile.ZipFile(artifact_file_local_path, 'r') as z:
            z.extractall(workspace_path)
        cleanups.append(('Remove downloaded artifact file', lambda: os.remove(artifact_file_local_path)))
    else:
        artifact_file_workspace_path = os.path.join(workspace_path, os.path.basename(artifact_key))
        shutil.move(artifact_file_local_path, artifact_file_workspace_path)

def run(cleanups, args, request, config, s3, response_poster):
    resource_properties = request['ResourceProperties']
    resource_group_name = terraform_resource_group.construct_resource_group_name(request)

    assume_role_arn = resource_properties['LaunchRoleArn']
    session_name = 'TerraformAssumeRoleSession-{}'.format(request['RequestId'])
    assume_role_input = AssumeRoleInput(assume_role_arn, args.external_id, session_name)

    # The user doesn't have the option to specify a dry-run argument (or any argument)
    # for delete requests, so dry-runs are not supported for delete requests.
    request_type = request['RequestType']
    request_is_dryrun = bool(resource_properties.get('DryRunId')) and request_type != 'Delete'

    # Try to retrieve the tags at the beginning. In case getting tags fails, it's
    # better to fail before Terraform creates all the resources rather than after.
    user_tags = None
    stack_arn = Arn(request['StackId'])
    if not request_is_dryrun and request_type in ['Create', 'Update']:
        user_tags = terraform_tag.retrieve_user_tags_from_cfn(stack_arn, assume_role_input)
        user_tags['CfnStackId'] = request['StackId']
        user_tags['TfResourceGroupName'] = resource_group_name

    # Set up directory of execution
    print('Creating workspace')
    root_workspace_path = os.path.expanduser(config['root-workspace-path'])
    physical_resource_id = request['PhysicalResourceId']
    workspace_path = os.path.join(root_workspace_path, physical_resource_id)
    os.makedirs(workspace_path)
    cleanups.append(('Remove workspace', lambda: shutil.rmtree(workspace_path)))

    artifact_url = resource_properties['TerraformArtifactUrl']
    artifact_file_local_path = os.path.join(root_workspace_path, physical_resource_id + '-file')
    download_artifact(s3, artifact_url, artifact_file_local_path, workspace_path, cleanups)

    print('Writing backend configuration to file')
    state_file_location = inject_backend_config(workspace_path, config, physical_resource_id)
    print('Creating AWS provider override file')
    stack_region = stack_arn.region
    inject_aws_provider_override(workspace_path, stack_region, assume_role_input)
    print('Writing variables to file')
    inject_variables(workspace_path, request['ResourceProperties'])

    print('Starting Terraform execution')
    os.chdir(workspace_path)
    os.environ['TF_IN_AUTOMATION'] = 'true'

    executor = TerraformExecutor(request_type)
    executor.init_workspace()

    if request_is_dryrun:
        executor.plan()
        # Even if dry run has error, mark success so user can try again.
        response_poster.post_response_with_expiration_check('SUCCESS')
        return

    proc = executor.start_request_command()
    signal_handler = lambda signum, frame: response_poster.post_timeout_response(proc.pid)
    signal.signal(signal.SIGALRM, signal_handler)
    # save additional time before the presigned s3 url timeout
    signal.alarm(seconds_until_expiry(request['ResponseURL']))
    executor.finish_request_command(proc)

    state = terraform_state.parse(s3, workspace_path, config['bucket'], physical_resource_id, stack_arn)
    arns = state.arns
    # Parse the terraform state file and add resource group tags
    if request['RequestType'] in ['Create', 'Update']:
        print('Tagging resources with tags: ' + str(user_tags))
        terraform_tag.tag_resources(arns, user_tags, stack_region, assume_role_input)

    if request['RequestType'] in ['Create', 'Update']:
        print('Creating resource group if not exist')
        terraform_resource_group.create_resource_group_if_not_exist(user_tags, assume_role_input)

    if request['RequestType'] == 'Delete':
        terraform_resource_group.delete_resource_group(resource_group_name, stack_region, assume_role_input)

    response_poster.post_response_with_expiration_check('SUCCESS',
                                                        arns=arns,
                                                        output_variables=state.outputs,
                                                        state_file_location=state_file_location)
    signal.alarm(0)    # Disable the alarm

def clean(cleanups):
    for step, cleanUp in cleanups:
        try:
            print(step)
            cleanUp()
        except Exception as e:
            print('Failed to {} because of the following error: '.format(step), e)

def main():
    cleanups = []
    args = parse_args()

    print("\n\n\n==========TERRAFORM WRAPPER SCRIPT OUTPUT==========")
    config = sc_config.load_config(verbose=True)
    wrapper_server_region = config['region']

    s3 = boto3.client('s3', region_name=wrapper_server_region)
    response_poster = ResponsePoster(s3, args, wrapper_server_region)

    try:
        validate_request_arg(args.request)
        run(cleanups, args, args.request, config, s3, response_poster)
    except Exception as e:
        msg = 'Encountered error during fulfillment script execution - ' + get_exception_msg(e)
        response_poster.post_response_with_expiration_check('FAILED', reason=msg)

        print(msg)
        print('Script error output is accessible to administrators in the fulfillment account at '
              'the following location:\n    s3://{}/{}'.format(args.output_bucket, args.error_key))
        raise e
    finally:
        clean(cleanups)

if __name__ == '__main__':
    main()
