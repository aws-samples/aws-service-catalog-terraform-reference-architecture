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