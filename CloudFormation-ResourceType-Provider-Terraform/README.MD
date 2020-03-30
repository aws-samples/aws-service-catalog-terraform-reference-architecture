# CloudFormation Resource Type Provider for Terraform

## Solution Overview

**AWS CloudFormation, AWS Service Catalog, and Terraform Terminology**  
- Terraform account: The account where the Terraform server or engine will live.

This solution requires that a Terraform server be deployed and available. You can use an existing Terraform server, 
or you can deploy a new Terraform server using the supplied CloudFormation templates. The supplied CloudFormation templates will create a new Amazon Elastic Compute Cloud (Amazon EC2) instance and install Terraform. 
One Terraform server is required for each AWS region.  

For the Terraform server to operate, you must create AWS Identity and Access Management (IAM) roles in each account.  You must register a CloudFormationRegistry **Cloudsoft::Terraform::Infrastructure**  resource type in each region you will use Terraform in and create the following IAM roles:

- Execution role. Role the Terraform server will use to deploy AWS resources.
- Logging role. Role the Terraform process will use to create log entries.

CloudFormation will use the **Cloudsoft::Terraform::Infrastructure** resource to communicate to the Terraform server using SSH protocol (SSH). The following information will be needed:

- Terraform DNS hostname or IP address
- SSH KeyPair
- SSH username
- SSH private key
- SSH port
- SSH fingerprint

Our custom provider creates and uses the following the following [AWS 
Systems Manager parameters](https://aws.amazon.com/systems-manager/

   - /cfn/terraform/logs-s3-bucket-name
   - /cfn/terraform/process-manager	
   - /cfn/terraform/ssh-username	
   - /cfn/terraform/ssh-fingerprint	
   - /cfn/terraform/ssh-host	
   - /cfn/terraform/ssh-key	
   - /cfn/terraform/ssh-port


The AWS CloudFormation template acts as a proxy to Terraform. To communicate with the Terraform server, it uses a  **Cloudsoft::Terraform::Infrastructure**  resource type.


---
Figure 1 shows the resulting architecture

![Solution Overview](documentation/images/cft_terraform.png) 

Figure 1. Architecture of Terraform customer resource on AWS CloudFormation


Once the solution is deployed. The CloudFormation and Terraform files are placed in an [Amazon Simple Storage Service (Amazon S3)](https://aws.amazon.com/s3/)
bucket. You can then launch the AWS CloudFormation wrapper files via AWS CloudFormation. You can also use them to create AWS Service Catalog products so that end-users with the proper permissions can launch them from the AWS Service Catalog console based on the Terraform CloudFormation wrapper file.


Either way, AWS CoudFormation uses the Cloudsoft::Terraform::Infrastructure resource to communicate with the Terraform server. After that, the Terraform server manages the AWS resources, and the resource provider logs the activity into an Amazon S3 bucket.
 
The procedures below add Terraform as a AWS CloudFormation resource type provder and create a Service Catalog product for others to use.

**Prerequisites**

This solution requires that a Terraform server be deployed and available. You can use an existing Terraform server or you can deploy a new Terraform server using the supplied AWS CloudFormation templates. The supplied AWS CloudFormation templates create a new [Amazon Elastic Compute Cloud (Amazon EC2)](https://aws.amazon.com/ec2/) instance and install Terraform.


One CloudFormation resource type is required for each AWS region.
 
For the Terraform server to operate, create AWS Identity and Access Management (IAM) roles in each account. Register a CloudFormationRegistry Cloudsoft::Terraform::Infrastructure resource type in each region you will use Terraform in and create the following IAM roles:
 
- Execution role — the role the Terraform server will use to deploy AWS resources.
- Logging role — the role the Terraform process will use to create log entries.
 
**Using the Terraform resource type in a multi-account environment.**
 
Multiple accounts can be configured to use a single Terraform instance. For example, in a Control Tower managed environment the Terraform instance can be placed in a shared services account. Other accounts can then configure their resource types to use the Terraform instance in the shared service account.

This GitHub content was created based on [this repo](https://github.com/cloudsoft/aws-cfn-connector-for-terraform)  created by Cloudsoft.

---
### How to Install CloudSoft’s Terraform provider 

You can install the Cloudsoft::Terraform::Infrastructure resource in two different ways:.

- Create a new Terraform server
- Use an existing Terraform server

We describe both below.

---
#### Option One – Creating a new Terraform Server

This method creates a demo AWS Elastic Cloud Computing (EC2) instance Terraform server and the required IAM execution and logging roles.

**Prerequisites**
Before you begin create the following if you don't already have:

- A security group with inbound access for port 22
- A keypair with access to the keypait.pem file

**Step 1. 	 Create the server.**

   - Navigate to the CloudFormation  [AWS CloudFormation Console-Create Demo TF Server](https://console.aws.amazon.com/cloudformation/home?#/stacks/new?stackName=cfrtrsetup&templateURL=https://kwdem0s.s3.amazonaws.com/cs/crt-tf-provider-setup.json) and launch a stack that will create the roles and the Terraform server.
   - Validate you are in the correct **AWS Region**
   - Choose **Next**
   - Parameters
      - **StackName** cfrtrsetup
      - **KeyName** KeyPair to use for the server - Choose one
      - **ParameterStoreSSHusername** **ec2-user** ssh-username to connect to the Terraform Server e.g. ubuntu
      - **ParameterStoreSShport** **22** port
      - **SecurityGroup** SecurityGroup Choose one *must be public accessible*
      - **SubnetID** Subnet Chose one *must be public accessible*

   - Choose **Next**.
   - On the **Configure stack options** page chose **Next**
   - On the **Review** page
      - Choose the check box for **I acknowledge that AWS CloudFormation might create IAM resources with custom names.**

   - Choose **Create Stack**
   - Wait for **Status** to change to **CREATE_COMPLETE**, as shown in Figure 2.
   - Choose the **Output** tab
      ![Output](documentation/images/cftrsetup002.png)
      
      Figure 2. The Output tab for a new Terraform server
   
**Step 2. Registering the Cloudsoft::Terraform::Infrastructure type**

   - Copy the **PublicDNSName** value 
   - Copy the **SetupScript** value from the **Output** tab

    Execute the following commands

   These commands will set the required environment values

   - Store the ssh fingerpront in a Systems Manager parameter
   - Register the new CloudFormation Third party Terraform resource type

   - Open a terminal or putty session
   - Goto the location of the keypair.pem file
   - Login via ssh using the user **ec2-user** 
   ```
   ssh -i ctr_de___s_e2.pem ec2-user@ec2-3-17-xxx-126.us-east-2.compute.amazonaws.com
   aws s3 cp s3://tues810-holdingbucket-5futiuffqpff/s00setup.sh s00setup.sh
   chmod u+x s00setup.sh
   ./s00setup.sh

   ```
   The sucessfull response will look similar to the following
   ![Output](documentation/images/cftrsetup003.png)

   - exit the ssh session

   ```
   exit
   ```
**Step 3. Registering tSave the private key into a Systems Manager paramter** 

   - Goto the location of the keypair.pem file
   - Copy the content of your private key
   - cat keypair.pem to see the content
   - copy from -----BEGIN RSA PRIVATE KEY----- -to- -----END RSA PRIVATE KEY-----
     ![Output](documentation/images/cftrsetup004.png)
   - Right click and open the **SystemsManager** URL in a new tab from the output tab
   - Choose the **/cfn/terraform/ssh-key** paramter
   - Choose **Edit** from the top right
   - Replace (FIXME) with the value you copied
   - Choose **Save changes**

---

### Option Two – Using an existing  Terraform server

**Step 1. Identify the existing server and configure it.** 

- Get the following information:

   - Terraform DNS hostname or IP address
   - SSH KeyPair
   - SSH username
   - SSH private key
   - SSH port
   - SSH fingerprint
   

- Navigate to the CloudFormation  [AWS CloudFormation Console-Create Demo TF Server](https://console.aws.amazon.com/cloudformation/home?#/stacks/new?stackName=cfrtrsetup&templateURL=https://kwdem0s.s3.amazonaws.com/cs/crt-tf-provider-setup-no-svr.json) and launch a stack that will create the roles and the Terraform server.
- Validate you are in the correct **AWS Region**
- Choose **Next**
- Parameters
   - **StackName** cfrtrsetup
   - **ParameterSSHHost**	
   - **ParameterSSHFingerprint**	
   - **ParameterSSHusername**
   - **ParameterSShport**
- Choose **Next**.
- On the **Configure stack options** page chose **Next**
- On the **Review** page
   - Choose the check box for **I acknowledge that AWS CloudFormation might create IAM resources with custom names.**
 - Choose **Create Stack**
- Wait for **Status** to change to **CREATE_COMPLETE**
- Choose the **Output tab**, as shown in Figure 3.
   ![Output](documentation/images/cftrsetup005.png)
   
   Figure 3. The Output tab for an existing Terraform server
   

**Step 2. Registering the Cloudsoft::Terraform::Infrastructure type**

- Copy the **PublicDNSName** value 
- Copy the **SetupScript** value from the **Output** tab

**Install the** [AWS CloudFormation Command Line Interface (CLI)](https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/what-is-cloudformation-cli.html) if needed 

   ```
   ssh -i ctr_de___s_e2.pem ec2-user@ec2-3-17-xxx-126.us-east-2.compute.amazonaws.com
   sudo apt update && sudo apt upgrade
   sudo apt install -y unzip
   sudo apt install -y python3-pip
   sudo pip3 install --upgrade pip
   curl "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" -o "awscli.zip"
   unzip awscli.zip
   sudo ln -s /usr/bin/python3 /usr/bin/python
   sudo ./awscli-bundle/install -i /usr/local/aws -b /usr/local/bin/aws
   ```

**Step 3 Register the new CloudFormation Third party Terraform resource type**

```
aws s3 cp s3://-from-the SetupScript you copied-above/s00setup.sh s00setup.sh
chmod u+x s00setup.sh
./s00setup.sh
```
- exit the ssh session

```
exit
```
**Save the private key into a Systems Manager paramter** 
   - Goto the location of the keypair.pem file
   - Copy the content of your private key
      - from -----BEGIN RSA PRIVATE KEY----- -to- -----END RSA PRIVATE KEY-----
     ![Output](documentation/images/cftrsetup004.png)
   - Right click and open the **SystemsManager** URL in a new tab from the output tab
   - Choose the **/cfn/terraform/ssh-key** paramter
   - Choose **Edit** from the top right
   - Replace (FIXME) with the value you copied
   - Choose **Save changes**

**That completes the setup process. If you want to create a Service Catalog product for others to use, follow the instructions below.**

---

### End User Section 

**Creating Service Catalog product (optional)**

**Step 1. Validate the CloudFormation Configuration**

   - Navigate to the CloudFormation  [AWS CloudFormation Conlole](https://console.aws.amazon.com/cloudformation/home)
   - Choose Resource types
   - Choose **Private** You should see Cloudsoft::Terraform::Infrastructure custom provider as shown in Figure 4.
      ![Output](documentation/images/cftrsetup006.png)

      Figure 4. Terraform Custom resource appearing in AWS CloudFormation resource types

---


**Step 2. Create a sample Service Catalog product.**

Configure the Service Catalog for your user, following these steps.

   - Navigate to the CloudFormation  [AWS CloudFormation Conlole](https://console.aws.amazon.com/cloudformation/home)
   - Choose the **demoportfolio9999** stack
   - Choose the **Outputs** tab
   - Right click on the **SamplesSCPortolio** URL and open it in a new tab
   - Choose the **Groups, roles, and users** tab
   - Choose the **Add groups, roles,users** button
   - Choose the **Users** tab
   - Select your user name
   - Choose the **Add Access** button

#### How to Use the Terraform Resource in AWS CloudFormation

Now that the resource provider is configured, you can use it in two ways:

- Directly with AWS CloudFormation 
- As a Service Catalog product (if you followed the optional procedure above)


**Step 1. Create the AWS CloudFormation Stack**

- Download the [example CloudFormation template] (https://github.com/cloudsoft/aws-cfn-connector-for-terraform/blob/master/terraform-example.cfn.yaml)
- Navigate to the CloudFormation [AWS CloudFormation Console](https://console.aws.amazon.com/cloudformation/home)
- Choose **Create Stack with new resources
- Choose **Upload a template file** and select the template downloaded previously 
- Choose **Next**
- Parameters
   - **CFNBucket1Name**
   - **CFNBucket2Name**
- Choose **Next**.
- On the Configure stack options page chose **Next**
- On the **Review** page
   - Choose the check box for **I acknowledge that AWS CloudFormation might create IAM resources with custom names.
- Choose **Create Stack**
- Wait for Status to change to **CREATE_COMPLETE**
- Choose the **Output tab** 




**Step 2. Launch the Service Catalog product**

   - Choose the **Products** link on the top left
   - Choose the three dots menu option to the left of **Bucket**
   - Choose **Launch product**
   - Enter a product name *mytestbucket*
   - Use defaults as you step throught the next screens
   - One the **Launch** screen choose **Launch**
   - Wait for the status to change to **Succeeded**
   - In the output sections you will see the outputs
   - The **LogBucketUrl** link will display the output from the terraform server, as shown in Figure 5.


      ![Output](documentation/images/cftrsetup0020.png)
      
      Figure 5. Output from the Terraform server

   ---


**Step 3 View the Sample Service Catalog portfolio**

   The setup script creates a sample Service Catalog portfolio with a private S3 product. You can view it by:
   
   - Right click on [AWS Service Catalog Conlole](https://console.aws.amazon.com/servicecatalog/home?) and open it in a new tab.
   - Choose the **Portfolios** link on the bottom left
   - Choose the **CloudFormationCustom** portfolio
   - Choose the **Bucket** product
   - Choose the **V1.01** 
   - Choose the **Template** tab to view the underlying template

---

**The terraform resource on CloudFormation is now available in the AWS Service Catalog.**
 

You can now use the provided samples to create additional CloudFormation wrapper and TF files for other services.

### Code Samples

You can use these code samples to create additional AWS CloudFormation wrapper and Terraform files for other services.

- CloudFormation Wrapper File

   ```
     {
       "AWSTemplateFormatVersion": "2010-09-09",
       "Description": "Terraform in CloudFormation S3 example",
       "Parameters": {
           "BucketNamePrefix": {
               "Type": "String",
               "Description": "Bucket name prefix",
               "Default": "cft123"
           }
       },
       "Resources": {
           "Terraformsample": {
               "Type": "Cloudsoft::Terraform::Infrastructure",
               "Properties": {
                   "ConfigurationUrl": "https://s3loction of your .tf file",
                   "Variables": {
                       "aws_region": {
                           "Ref": "AWS::Region"
                       },
                       "bucket_name": {
                           "Ref": "BucketNamePrefix"
                       }
                   }
               }
           }
       },
       "Outputs": {
           "LogBucketUrl": {
               "Value": {
                   "Fn::GetAtt": [
                       "Terraformsample",
                       "LogBucketUrl"
                   ]
               }
           },
           "tfOut": {
               "Value": {
                   "Fn::GetAtt": [
                       "Terraformsample",
                       "OutputsStringified"
                   ]
               }
           }
       }
   }   
   ```
**Terraform S3 Sample**

   ```
      variable "bucket_name" {
        type = "string"
      }
      variable "aws_region" {
        type = "string"
      }

      provider "aws" {
        region  = "${var.aws_region}"
        version = "~> 2.38"

      }


      resource "random_id" "ran_dom_suffix" {
        byte_length = 8
      }

      resource "aws_s3_bucket" "bucket" {
        bucket = "${var.bucket_name}${random_id.ran_dom_suffix.hex}"
        acl    = "private"
      }

      output webaddress {
        value = "${format("http://%s",aws_s3_bucket.bucket.bucket_domain_name )}"
      }

   ```

---

### Security Best Practices

**Update your Terraform binary version when you identify vulnerabilities**

If you become aware of a security vulnerability affecting the Terraform binary version installed on your Terraform  Server, switch to a version that is not affected by the vulnerability. 

To do this, update your Terraform Wrapper Server stack by setting the stack's TerraformVersion parameter to the version you want to switch to. Keep in mind that you may need to update your Terraform configurations if a backwards incompatible change has been introduced in the Terraform binary version to which you are switching.


**Limit access to the hub account**

The resource provider will use the execution role in AWS AIM. To limit the exposure of these roles, you should restrict access to the resource provider as much as. 

**Cleanup process**

To avoid incurring costs, delete resources that are not needed.  You can terminate the Service Catalog product deployed by selecting Action then Terminate.


**Conclusion**

By creating a custom AWS CloudFormation resource for Terraform, you can control your on-premises and public cloud resources programmatically. You can then access that resource either directly through the AWS CloudFormation console, or through the AWS Service Catalog, which gives you an extra layer of governance and control.

**Get more information about our solution here:**

   - https://github.com/cloudsoft/aws-cfn-connector-for-terraform/
   - https://www.terraform.io/intro/index.html
   - https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/what-is-cloudformation-cli.html
