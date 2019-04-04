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
from sc_terraform_wrapper.arn_format import ArnFormat
import json
import os
import pyjq

TerraformState = namedtuple('TerraformState', ['outputs', 'arns'])

TYPE_TO_ARN_FORMAT = {
    'aws_ami':                      ArnFormat('ec2', 'image/{}', ['id']),
    'aws_customer_gateway':         ArnFormat('ec2', 'customer-gateway/{}', ['id']),
    'aws_ebs_snapshot':             ArnFormat('ec2', 'snapshot/{}', ['id']),
    'aws_instance':                 ArnFormat('ec2', 'instance/{}', ['id']),
    'aws_internet_gateway':         ArnFormat('ec2', 'internet-gateway/{}', ['id']),
    'aws_network_acl':              ArnFormat('ec2', 'network-acl/{}', ['id']),
    'aws_network_interface':        ArnFormat('ec2', 'network-interface/{}', ['id']),
    'aws_route_table':              ArnFormat('ec2', 'route-table/{}', ['id']),
    'aws_spot_instance_request':    ArnFormat('ec2', 'spot-instances-request/{}', ['id']),
    'aws_subnet':                   ArnFormat('ec2', 'subnet/{}', ['id']),
    'aws_vpc':                      ArnFormat('ec2', 'vpc/{}', ['id']),
    'aws_vpc_dhcp_options':         ArnFormat('ec2', 'dhcp-options/{}', ['id']),
    'aws_vpn_connection':           ArnFormat('ec2', 'vpn-connection/{}', ['id']),
    'aws_vpn_gateway':              ArnFormat('ec2', 'vpn-gateway/{}', ['id']),
    'aws_elasticache_cluster':      ArnFormat('elasticache','cluster:{}', ['id']),
    'aws_emr_cluster':              ArnFormat('elasticmapreduce','cluster/{}', ['id']),
    'aws_db_snapshot':              ArnFormat('rds','snapshot:{}', ['id']),
    'aws_redshift_cluster':         ArnFormat('redshift','cluster:{}', ['id']),
    'aws_redshift_parameter_group': ArnFormat('redshift','parametergroup:{}', ['id']),
    'aws_redshift_subnet_group':    ArnFormat('redshift','subnetgroup:{}', ['id']),
    'aws_route53_health_check':     ArnFormat('route53','healthcheck/{}', ['id'], accountless=True, regionless=True),
    'aws_route53_zone':             ArnFormat('route53','hostedzone/{}', ['id'], accountless=True, regionless=True),
}

def parse(s3, workspace_path, bucket, physical_resource_id, stack_arn):
    state = _get_state(s3, workspace_path, bucket, physical_resource_id)
    outputs = _get_outputs(state)
    arns = _get_arns(state, stack_arn)
    return TerraformState(outputs, arns)

def _get_state(s3, workspace_path, bucket, physical_resource_id):
    key = '{}/terraform.tfstate'.format(physical_resource_id)
    terraform_state_file_path = os.path.join(workspace_path, os.path.basename(key))
    s3.download_file(bucket, key, terraform_state_file_path)

    with open(terraform_state_file_path) as f:
        return json.load(f)

def _get_outputs(state):
    exist_output = pyjq.first('.modules[].outputs', state)
    if not exist_output:
        return dict()
    else:
        return { k: v['value'] for k, v in exist_output.items() if 'value' in v }

def _get_arns(state, stack_arn):
    arns = _get_state_resource_arns(state)
    arns += _generate_resource_arns(state, stack_arn)
    return list(set(arns))

def _get_state_resource_arns(state):
    """Get the ARNs for all resources in the state file that have an "arn" attribute"""
    arns = pyjq.all('.modules[].resources[].primary.attributes.arn', state)
    return list(filter(lambda arn: arn and arn.startswith('arn:') and arn.count(':') >= 5, arns))

def _generate_resource_arns(state, stack_arn):
    """Construct ARNs for all resources in the state file that don't have an "arn" attribute"""
    resource_types = set(pyjq.all('.modules[].resources[].type', state))
    resource_types_without_arn = resource_types & TYPE_TO_ARN_FORMAT.keys()

    all_resources = pyjq.all('.modules[].resources[]', state)
    arns = []
    for resource_type in resource_types_without_arn:
        resources_without_arn = [r for r in all_resources if r.get('type') == resource_type]
        arn_format = TYPE_TO_ARN_FORMAT[resource_type]
        arns += [_generate_resource_arn(stack_arn, r, arn_format) for r in resources_without_arn]

    return arns

def _generate_resource_arn(stack_arn, resource, arn_format):
    resource_attributes = resource.get('primary', {}).get('attributes', {})
    suffix_attribute_values = [resource_attributes[attr] for attr in arn_format.suffix_attributes]
    return arn_format.get_arn(stack_arn.region, stack_arn.partition, stack_arn.account_id, *suffix_attribute_values)
