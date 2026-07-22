terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  # Remote state is recommended for real use. Create the bucket/table first,
  # then uncomment and run `terraform init -migrate-state`.
  # backend "s3" {
  #   bucket         = "jobtracker-tfstate-<your-suffix>"
  #   key            = "eks/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "jobtracker-tflock"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "jobtracker"
      ManagedBy = "terraform"
      Env       = var.environment
    }
  }
}
