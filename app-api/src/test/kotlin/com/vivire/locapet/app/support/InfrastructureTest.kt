package com.vivire.locapet.app.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import javax.sql.DataSource

class InfrastructureTest : IntegrationTestSupport() {

    @Autowired
    lateinit var dataSource: DataSource

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Test
    fun `PostgreSQL 컨테이너가 정상적으로 연결되어야 한다`() {
        // H2가 아니라 진짜 PostgreSQL Driver인지 확인
        val connection = dataSource.connection
        val metaData = connection.metaData

        println("🔥 Connected to: ${metaData.databaseProductName} ${metaData.databaseProductVersion}")

        assertThat(metaData.databaseProductName).isEqualTo("PostgreSQL")
        assertThat(connection.isValid(1)).isTrue()
    }

    @Test
    fun `Redis 컨테이너가 정상적으로 동작해야 한다`() {
        // Given
        val key = "test:key"
        val value = "Hello Testcontainers"

        // When
        redisTemplate.opsForValue().set(key, value)

        // Then
        val fetchedValue = redisTemplate.opsForValue().get(key)
        assertThat(fetchedValue).isEqualTo(value)
    }
}