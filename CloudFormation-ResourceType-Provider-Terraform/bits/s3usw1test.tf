data "aws_region" "current" {}

provider "aws" {
  region  = ""us-west-1""
  version = "~> 2.38"

}


resource "random_id" "ran_dom_suffix" {
  byte_length = 8
}
 
resource "aws_s3_bucket" "bucket" {
  bucket = "scb-${random_id.ran_dom_suffix.hex}cfrt-bucket01"
  acl    = "private"
}

output webaddress {
  value = "${format("http://%s",aws_s3_bucket.bucket.bucket_domain_name )}"
}