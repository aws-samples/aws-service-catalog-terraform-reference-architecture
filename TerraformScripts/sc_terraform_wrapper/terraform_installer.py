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

import argparse
import io
import os
import requests
import stat
import zipfile

def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument('version', help='The terraform version to install', nargs='?')
    return parser.parse_args()

def install_latest_terraform():
    args = parse_args()

    if args.version is None:
        checkpoint_response = requests.get('https://checkpoint-api.hashicorp.com/v1/check/terraform')
        checkpoint_response.raise_for_status()
        checkpoint = checkpoint_response.json()
        current_version = checkpoint['current_version']
    else:
        current_version = args.version
    download_url = "https://releases.hashicorp.com/terraform/{0}/terraform_{0}_linux_amd64.zip".format(current_version)

    terraform_response = requests.get(download_url)
    terraform_response.raise_for_status()
    terraform_zip_bytes = io.BytesIO(terraform_response.content)
    terraform_zip = zipfile.ZipFile(terraform_zip_bytes)

    terraform_zip.extract('terraform', '/usr/local/bin')
    os.chmod('/usr/local/bin/terraform', stat.S_IEXEC)
