output "app_api_repository_url" {
  value = aws_ecr_repository.app_api.repository_url
}

output "admin_api_repository_url" {
  value = aws_ecr_repository.admin_api.repository_url
}

output "vpc_id" {
  value = aws_vpc.nonprod.id
}

output "public_subnet_ids" {
  value = [
    aws_subnet.public_1.id,
    aws_subnet.public_2.id
  ]
}

output "private_db_subnet_ids" {
  value = [
    aws_subnet.private_db_1.id,
    aws_subnet.private_db_2.id
  ]
}

output "db_subnet_group_name" {
  value = aws_db_subnet_group.nonprod.name
}

output "redis_subnet_group_name" {
  value = aws_elasticache_subnet_group.redis.name
}

output "alb_security_group_id" {
  value = aws_security_group.alb.id
}

output "app_api_security_group_id" {
  value = aws_security_group.app_api.id
}

output "rds_security_group_id" {
  value = aws_security_group.rds.id
}

output "redis_security_group_id" {
  value = aws_security_group.redis.id
}

output "app_api_log_group_name" {
  value = aws_cloudwatch_log_group.app_api.name
}

output "admin_api_log_group_name" {
  value = aws_cloudwatch_log_group.admin_api.name
}

output "ecs_cluster_arn" {
  value = aws_ecs_cluster.nonprod.arn
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.nonprod.name
}

output "ecs_task_execution_role_arn" {
  value = aws_iam_role.ecs_task_execution.arn
}
