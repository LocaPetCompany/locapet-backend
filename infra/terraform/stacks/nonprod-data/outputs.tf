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

output "postgres_jdbc_url" {
  value = "jdbc:postgresql://${aws_db_instance.postgres.address}:${aws_db_instance.postgres.port}/${aws_db_instance.postgres.db_name}"
}
