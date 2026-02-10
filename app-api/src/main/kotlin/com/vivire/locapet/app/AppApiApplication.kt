package com.vivire.locapet.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.vivire.locapet"])
@EnableJpaRepositories(basePackages = ["com.vivire.locapet.domain"])
@EntityScan(basePackages = ["com.vivire.locapet.domain"])
class AppApiApplication

fun main(args: Array<String>) {
    runApplication<AppApiApplication>(*args)
}
