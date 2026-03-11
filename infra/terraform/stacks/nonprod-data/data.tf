data "terraform_remote_state" "bootstrap" {
  backend = "s3"

  config = {
    bucket = "vivire-locapet-terraform-state-apne2"
    key    = "nonprod/bootstrap/terraform.tfstate"
    region = "ap-northeast-2"
  }
}
