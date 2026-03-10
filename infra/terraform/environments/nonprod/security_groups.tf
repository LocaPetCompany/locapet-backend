resource "aws_security_group" "alb" {
  name        = "locapet-nonprod-alb-sg"
  description = "Allow HTTP traffic to public ALB"
  vpc_id      = aws_vpc.nonprod.id

  ingress {
    description = "HTTP from internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "locapet-nonprod-alb-sg"
    Project     = "locapet"
    Environment = "nonprod"
    Component   = "alb"
    ManagedBy   = "terraform"
  }
}

resource "aws_security_group" "app_api" {
  name        = "locapet-nonprod-app-api-sg"
  description = "Allow app-api traffic from ALB only"
  vpc_id      = aws_vpc.nonprod.id

  ingress {
    description     = "App API from ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "locapet-nonprod-app-api-sg"
    Project     = "locapet"
    Environment = "nonprod"
    Component   = "app-api"
    ManagedBy   = "terraform"
  }
}

resource "aws_security_group" "rds" {
  name        = "locapet-nonprod-rds-sg"
  description = "Allow PostgreSQL access from app runtime"
  vpc_id      = aws_vpc.nonprod.id

  ingress {
    description     = "PostgreSQL from app-api"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app_api.id]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "locapet-nonprod-rds-sg"
    Project     = "locapet"
    Environment = "nonprod"
    Component   = "rds"
    ManagedBy   = "terraform"
  }
}

resource "aws_security_group" "redis" {
  name        = "locapet-nonprod-redis-sg"
  description = "Allow Redis access from app runtime"
  vpc_id      = aws_vpc.nonprod.id

  ingress {
    description     = "Redis from app-api"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.app_api.id]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "locapet-nonprod-redis-sg"
    Project     = "locapet"
    Environment = "nonprod"
    Component   = "redis"
    ManagedBy   = "terraform"
  }
}