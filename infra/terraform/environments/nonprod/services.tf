resource "aws_ecs_service" "app_api" {
  name            = "locapet-dev-app-api"
  cluster         = aws_ecs_cluster.nonprod.id
  task_definition = aws_ecs_task_definition.app_api.arn
  launch_type     = "FARGATE"
  desired_count   = 1

  health_check_grace_period_seconds = 180

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets = [
      aws_subnet.public_1.id,
      aws_subnet.public_2.id
    ]

    security_groups = [
      aws_security_group.app_api.id
    ]

    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app_api.arn
    container_name   = "app-api"
    container_port   = 8080
  }

  depends_on = [
    aws_lb_listener.http
  ]

  tags = {
    Name        = "locapet-dev-app-api-service"
    Project     = "locapet"
    Environment = "dev"
    Component   = "app-api"
    ManagedBy   = "terraform"
  }
}