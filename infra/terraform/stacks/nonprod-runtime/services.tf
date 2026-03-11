resource "aws_ecs_service" "app_api" {
  name            = "locapet-dev-app-api"
  cluster         = data.terraform_remote_state.bootstrap.outputs.ecs_cluster_arn
  task_definition = aws_ecs_task_definition.app_api.arn
  launch_type     = "FARGATE"
  desired_count   = 1

  health_check_grace_period_seconds = 180

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = data.terraform_remote_state.bootstrap.outputs.public_subnet_ids
    security_groups  = [data.terraform_remote_state.bootstrap.outputs.app_api_security_group_id]
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
