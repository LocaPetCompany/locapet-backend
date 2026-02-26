#!/bin/bash
# =============================================================================
# Secrets Manager에서 시크릿을 가져와 .env 파일로 생성
# EC2 (dev) 배포 시 사용
#
# Usage:
#   ./scripts/fetch-secrets.sh [SECRET_NAME] [DEPLOY_DIR]
#   ./scripts/fetch-secrets.sh locapet/dev/app /opt/locapet
# =============================================================================

set -euo pipefail

SECRET_NAME="${1:-locapet/dev/app}"
DEPLOY_DIR="${2:-/opt/locapet}"
ENV_FILE="$DEPLOY_DIR/.env"

echo "Fetching secrets from: $SECRET_NAME"
echo "Writing to: $ENV_FILE"

SECRET_JSON=$(aws secretsmanager get-secret-value \
  --secret-id "$SECRET_NAME" \
  --query SecretString --output text)

if [ -z "$SECRET_JSON" ]; then
  echo "ERROR: Failed to fetch secret '$SECRET_NAME'"
  exit 1
fi

mkdir -p "$DEPLOY_DIR"

# JSON -> key=value 변환
echo "$SECRET_JSON" | jq -r 'to_entries[] | "\(.key)=\(.value)"' > "$ENV_FILE"

chmod 600 "$ENV_FILE"

echo "Secrets written to $ENV_FILE ($(wc -l < "$ENV_FILE") keys)"
