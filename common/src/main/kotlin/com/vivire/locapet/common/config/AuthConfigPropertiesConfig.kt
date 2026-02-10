package com.vivire.locapet.common.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(AuthConfigProperties::class)
class AuthConfigPropertiesConfig
