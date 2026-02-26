package com.vivire.locapet.admin

import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.runApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.vivire.locapet"])
@EnableJpaRepositories(basePackages = ["com.vivire.locapet.domain"])
@AutoConfigurationPackage(basePackages = ["com.vivire.locapet.domain"])
class AdminApiApplication

fun main(args: Array<String>) {
    runApplication<AdminApiApplication>(*args)
}
