package com.vivire.locapet.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.meta")
data class MetaConfigProperties(
    val storeUrls: StoreUrls,
    val baseUrl: BaseUrl,
    val policies: Policies
) {
    data class StoreUrls(
        val appStore: String,
        val playStore: String
    )

    data class BaseUrl(
        val api: String
    )

    data class Policies(
        val termsOfService: String,
        val privacyPolicy: String
    )
}
