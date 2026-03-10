output "app_api_repository_url" {
  value = aws_ecr_repository.app_api.repository_url
}

output "admin_api_repository_url" {
  value = aws_ecr_repository.admin_api.repository_url
}

output "postgres_endpoint" {
  value = aws_db_instance.postgres.address
}

output "postgres_port" {
  value = aws_db_instance.postgres.port
}

output "postgres_db_name" {
  value = aws_db_instance.postgres.db_name
}

output "postgres_master_username" {
  value = aws_db_instance.postgres.username
}

output "valkey_primary_endpoint" {
  value = aws_elasticache_replication_group.valkey.primary_endpoint_address
}

output "valkey_port" {
  value = aws_elasticache_replication_group.valkey.port
}