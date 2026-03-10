data "aws_secretsmanager_secret" "dev_shared" {
  name = "locapet/dev/shared"
}

data "aws_secretsmanager_secret" "dev_app_api" {
  name = "locapet/dev/app-api"
}

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

resource "aws_ecs_task_definition" "app_api" {
  family                   = "locapet-dev-app-api"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]

  cpu                      = "256"
  memory                   = "512"

  execution_role_arn       = aws_iam_role.ecs_task_execution.arn

  container_definitions = jsonencode([
    {
      name      = "app-api"
      image     = "${aws_ecr_repository.app_api.repository_url}:dev-latest"
      essential = true

      portMappings = [
        {
          containerPort = 8080
          hostPort      = 8080
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "dev"
        }
      ]

      secrets = [
        {
          name      = "DB_URL"
          valueFrom = "${data.aws_secretsmanager_secret.dev_shared.arn}:DB_URL::"
        },
        {
          name      = "DB_USERNAME"
          valueFrom = "${data.aws_secretsmanager_secret.dev_shared.arn}:DB_USERNAME::"
        },
        {
          name      = "DB_PASSWORD"
          valueFrom = "${data.aws_secretsmanager_secret.dev_shared.arn}:DB_PASSWORD::"
        },
        {
          name      = "REDIS_HOST"
          valueFrom = "${data.aws_secretsmanager_secret.dev_shared.arn}:REDIS_HOST::"
        },
        {
          name      = "REDIS_PORT"
          valueFrom = "${data.aws_secretsmanager_secret.dev_shared.arn}:REDIS_PORT::"
        },
        {
          name      = "JWT_SECRET"
          valueFrom = "${data.aws_secretsmanager_secret.dev_app_api.arn}:JWT_SECRET::"
        },
        {
          name      = "CI_HMAC_SECRET"
          valueFrom = "${data.aws_secretsmanager_secret.dev_app_api.arn}:CI_HMAC_SECRET::"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.app_api.name
          awslogs-region        = "ap-northeast-2"
          awslogs-stream-prefix = "ecs"
        }
      }
    }
  ])

  tags = {
    Name        = "locapet-dev-app-api-task-definition"
    Project     = "locapet"
    Environment = "dev"
    Component   = "app-api"
    ManagedBy   = "terraform"
  }
}

resource "aws_ecs_task_definition" "admin_api" {
  family                   = "locapet-dev-admin-api"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]

  cpu                = "256"
  memory             = "512"

  execution_role_arn = aws_iam_role.ecs_task_execution.arn

  container_definitions = jsonencode([
    {
      name      = "admin-api"
      image     = "${aws_ecr_repository.admin_api.repository_url}:dev-latest"
      essential = true

      portMappings = [
        {
          containerPort = 8081
          hostPort      = 8081
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "dev"
        }
      ]

      secrets = [
        {
          name      = "DB_URL"
          valueFrom = "${data.aws_secretsmanager_secret.dev_shared.arn}:DB_URL::"
        },
        {
          name      = "DB_USERNAME"
          valueFrom = "${data.aws_secretsmanager_secret.dev_shared.arn}:DB_USERNAME::"
        },
        {
          name      = "DB_PASSWORD"
          valueFrom = "${data.aws_secretsmanager_secret.dev_shared.arn}:DB_PASSWORD::"
        },
        {
          name      = "REDIS_HOST"
          valueFrom = "${data.aws_secretsmanager_secret.dev_shared.arn}:REDIS_HOST::"
        },
        {
          name      = "REDIS_PORT"
          valueFrom = "${data.aws_secretsmanager_secret.dev_shared.arn}:REDIS_PORT::"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.admin_api.name
          awslogs-region        = "ap-northeast-2"
          awslogs-stream-prefix = "ecs"
        }
      }
    }
  ])

  tags = {
    Name        = "locapet-dev-admin-api-task-definition"
    Project     = "locapet"
    Environment = "dev"
    Component   = "admin-api"
    ManagedBy   = "terraform"
  }
}