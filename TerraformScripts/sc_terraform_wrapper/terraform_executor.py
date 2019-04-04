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

from pkg_resources import parse_version
import os
import re
import subprocess
import sys

class TerraformExecutor:
    def __init__(self, request_type):
        self.request_command = TerraformExecutor._get_request_command(request_type)
        self.terraform_version = TerraformExecutor._get_terraform_version(self.request_command)

    @staticmethod
    def _get_request_command(request_type):
        return 'destroy' if request_type== 'Delete' else 'apply'

    @staticmethod
    def _get_terraform_version(request_command):
        try:
            version_bytes = subprocess.check_output(['terraform', 'version'])
            version_str = version_bytes.decode('utf-8')
            version_matches = re.findall('v(\d+\.\d+\.\d+)$', version_str, flags=re.M)

            if len(version_matches) == 1:
                return parse_version(version_matches[0])
            else:
                raise Exception('Unable to determine terraform version. `terraform version` output'
                    'has unexpected format: {}. '
                    'Skipping `terraform {}`'.format(version_str, request_command))

        except subprocess.CalledProcessError as e:
            exit_code = e.returncode
            raise Exception('Unable to determine terraform version. `terraform version` finished '
                            'with exit code {}. '
                            'Skipping `terraform {}`'.format(e.returncode, request_command))

    def init_workspace(self):
        command_tokens = ['terraform', 'init', '-input=false', '-no-color']
        exit_code = subprocess.call(command_tokens, stderr=sys.stdout.buffer)

        if exit_code != 0:
            raise Exception('`terraform init` finished with exit code {}. '
                            'Skipping `terraform {}`'.format(exit_code, self.request_command))

    def plan(self):
       subprocess.call(['terraform', 'plan', '-input=false', '-no-color'], stderr=sys.stdout.buffer)

    def start_request_command(self):
        if self.request_command == 'apply':
            return self.start_apply()
        else:
            return self.start_destroy()

    def start_apply(self):
        return subprocess.Popen(['terraform', 'apply', '-input=false', '-auto-approve', '-no-color'], stderr=sys.stdout.buffer, preexec_fn=os.setsid)

    def start_destroy(self):
        command_tokens = ['terraform', 'destroy', '-input=false', '-no-color']
        if self.terraform_version >= parse_version('0.11.4'):
            command_tokens.append('-auto-approve')
        else:
            command_tokens.append('-force')
        return subprocess.Popen(command_tokens, stderr=sys.stdout.buffer, preexec_fn=os.setsid)

    def finish_request_command(self, proc):
        exit_code = proc.wait()
        if exit_code != 0:
            raise Exception('`terraform {}` finished with exit code {}'.format(self.request_command, exit_code))