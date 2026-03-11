resource "aws_cloudwatch_log_group" "app_api" {
  name              = "/ecs/locapet-dev-app-api"
  retention_in_days = 7

  tags = {
    Name        = "locapet-dev-app-api-log-group"
    Project     = "locapet"
    Environment = "dev"
    Component   = "app-api"
    ManagedBy   = "terraform"
  }
}

resource "aws_cloudwatch_log_group" "admin_api" {
  name              = "/ecs/locapet-dev-admin-api"
  retention_in_days = 7

  tags = {
    Name        = "locapet-dev-admin-api-log-group"
    Project     = "locapet"
    Environment = "dev"
    Component   = "admin-api"
    ManagedBy   = "terraform"
  }
}

resource "aws_ecs_cluster" "nonprod" {
  name = "locapet-nonprod"

  tags = {
    Name        = "locapet-nonprod"
    Project     = "locapet"
    Environment = "nonprod"
    ManagedBy   = "terraform"
  }
}
