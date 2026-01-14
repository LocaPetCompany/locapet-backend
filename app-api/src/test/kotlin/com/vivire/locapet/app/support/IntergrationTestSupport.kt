package com.vivire.locapet.app.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers


@SpringBootTest // 통합 테스트 환경 로드
@Testcontainers // 이 클래스가 테스트 컨테이너를 관리한다고 선언
abstract class IntegrationTestSupport {

    companion object {
        // 1. MySQL 컨테이너 정의
        @Container // 테스트 시작 시 자동으로 Docker 컨테이너를 실행
        @ServiceConnection // ✨ 마법의 어노테이션! (아래 설명 참조)
        val mysqlContainer = MySQLContainer("mysql:8.0").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }

        // 2. Redis 컨테이너 정의 (GenericContainer 사용)
        @Container
        @ServiceConnection(name = "redis") // Redis 설정 자동 주입
        val redisContainer = GenericContainer("redis:alpine").apply {
            withExposedPorts(6379)
        }
    }
}