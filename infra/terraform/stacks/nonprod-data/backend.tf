terraform {
  backend "s3" {
    bucket         = "vivire-locapet-terraform-state-apne2"
    key            = "nonprod/data/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "locapet-terraform-locks"
    encrypt        = true
  }
}
