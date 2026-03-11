resource "aws_lb" "public" {
  name               = "locapet-dev-alb"
  internal           = false
  load_balancer_type = "application"

  security_groups = [aws_security_group.alb.id]
  subnets = [
    aws_subnet.public_1.id,
    aws_subnet.public_2.id
  ]

  enable_deletion_protection = false

  tags = {
    Name        = "locapet-dev-alb"
    Project     = "locapet"
    Environment = "dev"
    Component   = "alb"
    ManagedBy   = "terraform"
  }
}

resource "aws_lb_target_group" "app_api" {
  name        = "locapet-dev-app-api-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.nonprod.id
  target_type = "ip"

  health_check {
    enabled             = true
    path                = "/actuator/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
    interval            = 30
  }

  tags = {
    Name        = "locapet-dev-app-api-tg"
    Project     = "locapet"
    Environment = "dev"
    Component   = "app-api"
    ManagedBy   = "terraform"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.public.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app_api.arn
  }

  tags = {
    Name        = "locapet-dev-http-listener"
    Project     = "locapet"
    Environment = "dev"
    ManagedBy   = "terraform"
  }
}