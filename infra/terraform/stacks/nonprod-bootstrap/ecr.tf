resource "aws_ecr_repository" "app_api" {
  name                 = "locapet/app-api"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Project     = "locapet"
    Environment = "nonprod"
    Service     = "app-api"
    ManagedBy   = "terraform"
  }
}

resource "aws_ecr_repository" "admin_api" {
  name                 = "locapet/admin-api"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Project     = "locapet"
    Environment = "nonprod"
    Service     = "admin-api"
    ManagedBy   = "terraform"
  }
}
