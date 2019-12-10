# Terraform template to create an S3 Bucket with static website hosting

variable "bucket_name" {
  type = string
}
variable "aws_region" {
  type = string
}
provider "aws" {
  region = var.aws_region
}

resource "random_id" "ran_dom_suffix" {
  byte_length = 8
}

locals {
	bucketname = "${var.bucket_name}${random_id.ran_dom_suffix.hex}"
}    
	
resource "aws_s3_bucket" "bucket" {
  bucket = local.bucketname
  acl    = "private"
  website {
    index_document = "index.html"
    error_document = "error.html"
 }
}

resource "aws_s3_bucket_object" "bucket_index" {
  depends_on = [aws_s3_bucket.bucket]
  bucket = local.bucketname
  key = "index.html"
  content = "<h1>My Sample Website!</h1>"
  content_type = "text/html"
  acl    = "public-read"
}

resource "aws_s3_bucket_object" "bucket_error" {
  depends_on = [aws_s3_bucket.bucket]
  bucket = local.bucketname
  key = "error.html"
  content = "<h1>OOOPS!</h1> <p>There was an error!</p>"
  content_type = "text/html"
  acl    = "public-read"
}

output webaddress {
  value = "${format("http://%s",aws_s3_bucket.bucket.website_endpoint)}"
}
