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

from sc_terraform_wrapper.cfn_url_parser import seconds_until_expiry
import boto3
import json
import os
import random
import requests
import sc_terraform_wrapper.terraform_utils as terraform_utils
import signal
import string

class ResponsePoster:
    def __init__(self, s3, args, output_bucket_region):
        self.s3 = s3
        self.request = args.request
        self.output_bucket = args.output_bucket
        self.output_key = args.output_key
        self.output_bucket_region = output_bucket_region

    def post_response_with_expiration_check(self, status, arns=None, output_variables=None,
                                            state_file_location=None, reason=''):
        response_url = self.request['ResponseURL']
        if (seconds_until_expiry(response_url) <= 0):
            print('The cloudformation response url has expired. Not posting {} response to {}'.format(status, response_url))
        else:
            self._post_response(status, arns=arns, output_variables=output_variables,
                                state_file_location=state_file_location, reason=reason)

    def _post_response(self, status, arns=None, output_variables=None, state_file_location=None,
                       reason=''):
        print('Posting {} response to {}'.format(status, self.request['ResponseURL']))
        output_url = self.create_proxy_object()

        if status == 'FAILED' and self.request['RequestType'] == 'Update':
            # We ignore update-rollback request in the Lambda
            reason += '. Note that rollback will not be performed'

        response = {
            'Status' : status,
            'Reason' : reason + '. Terraform wrapper script output at: ' + output_url,
            'PhysicalResourceId' : self.request['PhysicalResourceId'],
            'StackId' : self.request['StackId'],
            'RequestId' : self.request['RequestId'],
            'LogicalResourceId' : self.request['LogicalResourceId'],
            'Data': {
                'ResourceArns': ",".join(arns) if arns else [],
                'TerraformScriptOutputLocation': output_url,
                'TerraformStateFileLocation' : state_file_location,
                'DryRunId': self.request['ResourceProperties'].get('DryRunId', ''),
                'Outputs': json.dumps(output_variables)
            },
        }

        r = requests.put(self.request['ResponseURL'], data=json.dumps(response), headers={'Content-Type': ''})
        if r.status_code != 200:
            print('Post response to {} failed with exit code {} because {}.'.format(self.request['ResponseURL'], r.status_code, r.raise_for_status()))

    def create_proxy_object(self):
        presigned_url = self.generate_presigned_url()

        suffix = ''.join(random.sample(string.ascii_letters + string.digits, 10))
        proxy_object_key = 'redirects/'+ suffix
        self.s3.put_object(
            ACL='public-read',
            Body=b'',
            Bucket=self.output_bucket,
            Key=proxy_object_key,
            WebsiteRedirectLocation=presigned_url
        )

        return "http://{bucket}.s3-website.{region}.amazonaws.com/{key}".format(
            bucket=self.output_bucket,
            key=proxy_object_key,
            region=self.output_bucket_region)

    def generate_presigned_url(self):
        ONE_WEEK_IN_SECONDS = 60 * 60 * 24 * 7
        return self.s3.generate_presigned_url('get_object',
            Params={'Bucket' : self.output_bucket, 'Key' : self.output_key},
            ExpiresIn=ONE_WEEK_IN_SECONDS)

    def post_timeout_response(self, process_id):
        msg = 'CloudFormation CustomResources automatically timeout after two hours. '
        if self.request['RequestType'] == 'Create':
            msg += 'The TerraformWrapperServer has cancelled any pending Terraform operations'
        else:
            msg += 'However, the TerraformWrapperServer will continue to apply any pending Terraform configurations'
        self._post_response('FAILED', reason=msg)
        # Kill the process if the request type is Create since the user cannot update the
        # CloudFormation stack any further if a Create fails. For Update, the user can wait
        # until the process finishes before they post subsequent updates. We already prevent
        # concurrent update on the Lambda side.
        if self.request['RequestType'] == 'Create':
            print('Gracefully shutting down Terraform execution with process id {}'.format(process_id))
            try:
                os.killpg(os.getpgid(process_id), signal.SIGTERM)
            except ProcessLookupError as e:
                print('Terraform execution with process id {} already completed.'.format(process_id))

