data "terraform_remote_state" "bootstrap" {
  backend = "s3"

  config = {
    bucket = "vivire-locapet-terraform-state-apne2"
    key    = "nonprod/bootstrap/terraform.tfstate"
    region = "ap-northeast-2"
  }
}

data "terraform_remote_state" "data" {
  backend = "s3"

  config = {
    bucket = "vivire-locapet-terraform-state-apne2"
    key    = "nonprod/data/terraform.tfstate"
    region = "ap-northeast-2"
  }
}

data "aws_secretsmanager_secret" "dev_shared" {
  name = "locapet/dev/shared"
}

data "aws_secretsmanager_secret" "dev_app_api" {
  name = "locapet/dev/app-api"
}
