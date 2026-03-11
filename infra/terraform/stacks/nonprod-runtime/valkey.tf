resource "aws_elasticache_replication_group" "valkey" {
  replication_group_id = "locapet-nonprod-valkey"
  description          = "Valkey for Locapet nonprod"

  engine    = "valkey"
  node_type = "cache.t4g.micro"
  port      = 6379

  num_cache_clusters         = 1
  automatic_failover_enabled = false
  multi_az_enabled           = false

  subnet_group_name  = data.terraform_remote_state.bootstrap.outputs.redis_subnet_group_name
  security_group_ids = [data.terraform_remote_state.bootstrap.outputs.redis_security_group_id]

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
