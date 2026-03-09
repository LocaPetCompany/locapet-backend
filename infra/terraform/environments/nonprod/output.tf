output "app_api_repository_url" {
  value = aws_ecr_repository.app_api.repository_url
}

output "admin_api_repository_url" {
  value = aws_ecr_repository.admin_api.repository_url
}
