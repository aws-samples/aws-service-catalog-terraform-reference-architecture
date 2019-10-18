variable "aws_region" {
  type = "string"
}
variable "aws_ami" {
  type = "string"
}
variable "key_name" {
  type = "string"
}
variable "aws_sg" {
  type = "string"
}
variable "aws_subnet" {
  type = "string"
}

# Specify the provider and access details

provider "aws" {
  region = "${var.aws_region}"
}

resource "random_id" "server_name_suffix" {
  byte_length = 8
}

resource "aws_instance" "web" {
  connection {
    user                      = "ec2-user"
  }
  instance_type               = "t2.micro"
  tags                        = {
    Name                      = "webb_${random_id.server_name_suffix.hex}"
  }
  ami                         = "${var.aws_ami}"
  key_name                    = "${var.key_name}"
  vpc_security_group_ids      = ["${var.aws_sg}"]
  associate_public_ip_address = true
  subnet_id                   = "${var.aws_subnet}"
}

resource "aws_instance" "db" {
  connection {
    user                      = "ec2-user"
  }
  instance_type               = "t2.micro"
  ami                         = "${var.aws_ami}"
  key_name                    = "${var.key_name}"
  vpc_security_group_ids      = ["${var.aws_sg}"]
  subnet_id                   = "${var.aws_subnet}"
  tags = {
    Name                      = "database_${random_id.server_name_suffix.hex}"
  }
  associate_public_ip_address = true
}
 
output webserver {
  value = "${aws_instance.web.*.public_dns[0]}"
}
output "dbserver" {
  value = "${aws_instance.db.*.public_dns[0]}"
}