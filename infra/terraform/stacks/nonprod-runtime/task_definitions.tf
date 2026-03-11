resource "aws_ecs_task_definition" "app_api" {
  family                   = "locapet-dev-app-api"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]

  cpu    = "256"
  memory = "512"

  execution_role_arn = data.terraform_remote_state.bootstrap.outputs.ecs_task_execution_role_arn

  container_definitions = jsonencode([
    {
      name      = "app-api"
      image     = "${data.terraform_remote_state.bootstrap.outputs.app_api_repository_url}:dev-latest"
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
        },
        {
          name  = "DB_URL"
          value = data.terraform_remote_state.data.outputs.postgres_jdbc_url
        },
        {
          name  = "DB_USERNAME"
          value = data.terraform_remote_state.data.outputs.postgres_master_username
        },
        {
          name  = "REDIS_HOST"
          value = aws_elasticache_replication_group.valkey.primary_endpoint_address
        },
        {
          name  = "REDIS_PORT"
          value = tostring(aws_elasticache_replication_group.valkey.port)
        }
      ]

      secrets = [
        {
          name      = "DB_PASSWORD"
          valueFrom = "${data.aws_secretsmanager_secret.dev_shared.arn}:DB_PASSWORD::"
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
          awslogs-group         = data.terraform_remote_state.bootstrap.outputs.app_api_log_group_name
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

  cpu    = "256"
  memory = "512"

  execution_role_arn = data.terraform_remote_state.bootstrap.outputs.ecs_task_execution_role_arn

  container_definitions = jsonencode([
    {
      name      = "admin-api"
      image     = "${data.terraform_remote_state.bootstrap.outputs.admin_api_repository_url}:dev-latest"
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
        },
        {
          name  = "DB_URL"
          value = data.terraform_remote_state.data.outputs.postgres_jdbc_url
        },
        {
          name  = "DB_USERNAME"
          value = data.terraform_remote_state.data.outputs.postgres_master_username
        },
        {
          name  = "REDIS_HOST"
          value = aws_elasticache_replication_group.valkey.primary_endpoint_address
        },
        {
          name  = "REDIS_PORT"
          value = tostring(aws_elasticache_replication_group.valkey.port)
        }
      ]

      secrets = [
        {
          name      = "DB_PASSWORD"
          valueFrom = "${data.aws_secretsmanager_secret.dev_shared.arn}:DB_PASSWORD::"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = data.terraform_remote_state.bootstrap.outputs.admin_api_log_group_name
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
