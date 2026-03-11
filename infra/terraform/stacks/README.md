# Terraform Stack Split

현재 `infra/terraform/environments/nonprod` 는 단일 state로 모든 리소스를 관리합니다.
이 디렉터리는 이를 `bootstrap / data / runtime` 3개 스택으로 나누기 위한 새 구조입니다.

중요:
- 이 구조는 기존 단일 state를 자동으로 이동하지 않습니다.
- 현재 실제 리소스의 source of truth 는 여전히 `environments/nonprod` 입니다.
- 새 스택으로 전환하려면 별도의 state migration 또는 import 작업이 필요합니다.

## Recommended Split

### `nonprod-bootstrap`
항상 유지해도 비용이 거의 없거나 매우 낮은 리소스입니다.

- ECR
- IAM execution role
- VPC / subnet / route / security group
- DB / Valkey subnet group
- CloudWatch log group
- ECS cluster

### `nonprod-data`
데이터 보존 관점에서 따로 관리하는 리소스입니다.

- RDS PostgreSQL

운영 방식:
- 안 쓸 때는 stop/start
- 장기 미사용이면 destroy 검토

### `nonprod-runtime`
필요할 때만 apply/destroy 하는 리소스입니다.

- Valkey
- ALB
- target group
- listener
- ECS task definition
- ECS service

Valkey를 runtime에 둔 이유:
- node-based Valkey는 stop 기능이 없습니다
- dev 에서는 세션/캐시 데이터가 상대적으로 disposable 하기 때문입니다

## Apply Order

1. `nonprod-bootstrap`
2. `nonprod-data`
3. `nonprod-runtime`

## Shutdown Order

1. `nonprod-runtime` destroy
2. `nonprod-data`
   - 우선 RDS stop 권장
   - 필요 시 destroy

## Always-On Cost Guide

아래는 "항상 유지" 대상으로 추천한 리소스의 비용 성격입니다.
정확한 서울 리전 금액은 AWS Pricing Calculator로 다시 확인해야 합니다.

| Resource | Why Keep | Pricing Model | Rough Monthly at Small Scale |
| --- | --- | --- | --- |
| IAM / OIDC / ECS execution role | 권한 기반 리소스라 비용 거의 없음 | direct charge 없음 | `$0` |
| VPC / subnet / route table / security group | 네트워크 바닥, 직접 과금 거의 없음 | direct charge 없음 | `$0` |
| DB / Valkey subnet group | 직접 과금 없음 | direct charge 없음 | `$0` |
| ECS cluster / task definitions | ECS 제어 plane 자체는 저렴 | direct charge 없음 | `$0` |
| CloudWatch log groups | 로그가 거의 없으면 매우 작음 | storage + API | `거의 0` |
| S3 backend bucket | tfstate 저장량이 매우 작음 | storage + requests | `거의 0` ~ `수 센트` |
| DynamoDB lock table | Terraform 실행 때만 읽기/쓰기 | on-demand requests + storage | `거의 0` |
| ECR repositories | 이미지 저장소로 유지 가치 큼 | stored image GB-month | `수 센트 ~ <$1` |
| Secrets Manager (`locapet/dev/shared`, `locapet/dev/app-api`) | 재생성 번거로움, runtime이 참조 | secret-month + API calls | `약 $0.80 + 소액 호출비` |

## Migration Note

새 스택을 실제 운영에 쓰려면 다음 중 하나가 필요합니다.

- 기존 `environments/nonprod` state 에서 새 stack state 로 `terraform state mv`
- 새 stack에서 `terraform import`

권장:
- bootstrap 먼저 옮기기
- data 옮기기
- 마지막에 runtime 옮기기
