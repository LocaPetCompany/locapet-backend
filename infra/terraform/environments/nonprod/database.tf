resource "aws_db_instance" "postgres" {
  identifier             = "locapet-nonprod-postgres"
  engine                 = "postgres"
  instance_class         = "db.t4g.micro"

  allocated_storage      = 20
  storage_type           = "gp3"
  storage_encrypted      = true

  db_name                = "locapet"
  username               = var.db_master_username
  password               = var.db_master_password
  port                   = 5432

  db_subnet_group_name   = aws_db_subnet_group.nonprod.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  multi_az               = false
  backup_retention_period = 1
  apply_immediately      = true
  deletion_protection    = false
  skip_final_snapshot    = true

  tags = {
    Name        = "locapet-nonprod-postgres"
    Project     = "locapet"
    Environment = "nonprod"
    Component   = "rds"
    ManagedBy   = "terraform"
  }
}

resource "aws_elasticache_replication_group" "valkey" {
  replication_group_id       = "locapet-nonprod-valkey"
  description                = "Valkey for Locapet nonprod"

  engine                     = "valkey"
  node_type                  = "cache.t4g.micro"
  port                       = 6379

  num_cache_clusters         = 1
  automatic_failover_enabled = false
  multi_az_enabled           = false

  subnet_group_name          = aws_elasticache_subnet_group.redis.name
  security_group_ids         = [aws_security_group.redis.id]

  apply_immediately          = true
  auto_minor_version_upgrade = true

  tags = {
    Name        = "locapet-nonprod-valkey"
    Project     = "locapet"
    Environment = "nonprod"
    Component   = "valkey"
    ManagedBy   = "terraform"
  }
}