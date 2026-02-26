package com.vivire.locapet.app.global.auth.identity

import com.vivire.locapet.common.config.AuthConfigProperties
import org.springframework.stereotype.Component
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class CiHashProvider(private val config: AuthConfigProperties) {

    fun hash(ci: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(config.identityVerification.ciHmacSecret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(ci.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
