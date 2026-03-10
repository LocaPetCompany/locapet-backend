# Locapet Dev Deployment Checklist

## Phase 0. Baseline
- [x] AWS 리전 고정: `ap-northeast-2`
- [x] GitHub Actions OIDC 인증 성공
- [x] Terraform backend 구성 완료
  - [x] S3 bucket
  - [x] DynamoDB lock table
- [x] ECR 생성 완료
  - [x] `locapet/app-api`
  - [x] `locapet/admin-api`
- [x] GitHub Actions `release-dev*` -> ECR push 성공
- [ ] `.terraform.lock.hcl` 커밋 여부 정리
- [ ] GitHub Actions role 분리 계획 정리
  - [ ] 현재는 통합 role 사용
  - [ ] 이후 `terraform role` / `deploy role` 분리

## Phase 1. Network
- [ ] nonprod VPC 생성
- [ ] AZ 2개 선택
- [ ] public subnet 2개 생성
- [ ] private DB subnet 2개 생성
- [ ] Internet Gateway 연결
- [ ] public route table 구성
- [ ] DB subnet group 생성
- [ ] Security Group 설계
  - [ ] ALB SG
  - [ ] app-api ECS SG
  - [ ] RDS SG
  - [ ] Redis SG
- [ ] DB/Redis는 public ingress 없이 private subnet에만 배치

## Phase 2. Data Layer
- [ ] RDS PostgreSQL 생성
  - [ ] nonprod 단일 인스턴스
  - [ ] UTC 설정 반영
- [ ] Redis 생성
  - [ ] nonprod 최소 사양
- [ ] Secrets Manager 구조 정리
  - [ ] `locapet/dev/shared`
  - [ ] `locapet/dev/app-api`
- [ ] Spring env var 기준으로 secret key 이름 정리
- [ ] DB / Redis connection 정보 확인

## Phase 3. Runtime Foundation
- [ ] CloudWatch log group 생성
  - [ ] `/ecs/locapet-dev-app-api`
  - [ ] `/ecs/locapet-dev-admin-migration`
- [ ] ECS cluster 생성
- [ ] ECS task execution role 생성
  - [ ] ECR pull
  - [ ] CloudWatch logs write
  - [ ] Secrets read
- [ ] `app-api` task definition 작성
  - [ ] image: `locapet/app-api:dev-latest`
  - [ ] port: `8080`
  - [ ] env / secrets 연결
  - [ ] timezone env 반영
- [ ] `admin-api` migration task definition 작성
  - [ ] image: `locapet/admin-api:dev-latest`
  - [ ] one-off task 용도
- [ ] health check 기준 확정
  - [ ] `/actuator/health`

## Phase 4. Ingress
- [ ] public ALB 생성
- [ ] app-api target group 생성
  - [ ] health check path: `/actuator/health`
- [ ] listener 80 생성
- [ ] dev 첫 검증은 ALB 기본 DNS 사용
- [ ] `admin-api`는 public 노출하지 않음

## Phase 5. First Dev Deploy
- [ ] `admin-api` one-off task로 migration 실행
- [ ] `app-api` ECS service 생성
  - [ ] desired count 1
  - [ ] target group 연결
- [ ] ECS task가 `dev-latest` 이미지를 pull 하는지 확인
- [ ] ALB DNS `/actuator/health` 확인
- [ ] app-api smoke test 1개 이상 확인
- [ ] CloudWatch logs에서 startup / DB / Redis 연결 확인

## Phase 6. CD Stabilization
- [ ] 현재 `release-dev*` -> ECR push workflow 유지
- [ ] dev deploy workflow 방향 결정
  - [ ] 수동 deploy
  - [ ] force-new-deployment
  - [ ] 별도 deploy workflow
- [ ] `dev-latest` 기반 재배포 절차 문서화

## Phase 7. Staging / Prod Expansion
- [ ] staging/prod naming rule 정리
- [ ] nonprod Terraform 구조를 module 기준으로 정리
- [ ] Route53 / ACM / HTTPS phase 분리
- [ ] prod 전용 DB / Redis / 계정 분리 계획 정리

## Current Decisions
- [x] 환경명 기준: `dev / staging / prod`
- [x] 첫 dev 배포는 `app-api`만 서비스로 배포
- [x] `admin-api`는 상시 서비스가 아니라 migration one-off task 용도
- [x] dev 첫 외부 검증은 ALB 기본 DNS 사용
- [x] dev ECS first deploy image tag는 `dev-latest`
- [x] `admin-api`는 내부 전용
