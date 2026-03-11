resource "aws_db_instance" "postgres" {
  identifier     = "locapet-nonprod-postgres"
  engine         = "postgres"
  instance_class = "db.t4g.micro"

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "locapet"
  username = var.db_master_username
  password = var.db_master_password
  port     = 5432

  db_subnet_group_name   = data.terraform_remote_state.bootstrap.outputs.db_subnet_group_name
  vpc_security_group_ids = [data.terraform_remote_state.bootstrap.outputs.rds_security_group_id]
  publicly_accessible    = false

  multi_az                = false
  backup_retention_period = 1
  apply_immediately       = true
  deletion_protection     = false
  skip_final_snapshot     = true

  tags = {
    Name        = "locapet-nonprod-postgres"
    Project     = "locapet"
    Environment = "nonprod"
    Component   = "rds"
    ManagedBy   = "terraform"
  }
}
