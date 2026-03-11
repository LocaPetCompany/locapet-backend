output "valkey_primary_endpoint" {
  value = aws_elasticache_replication_group.valkey.primary_endpoint_address
}

output "valkey_port" {
  value = aws_elasticache_replication_group.valkey.port
}

output "alb_dns_name" {
  value = aws_lb.public.dns_name
}

output "app_api_service_name" {
  value = aws_ecs_service.app_api.name
}
