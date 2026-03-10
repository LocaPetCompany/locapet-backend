variable "db_master_username" {
  description = "Master username for nonprod PostgreSQL"
  type        = string
  default     = "locapet_admin"
}

variable "db_master_password" {
  description = "Master password for nonprod PostgreSQL"
  type        = string
  sensitive   = true
}