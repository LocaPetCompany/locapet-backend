package com.vivire.locapet.app.global.auth.identity

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["app.auth.identity-verification.provider"], havingValue = "real")
class RealIdentityVerificationProvider : IdentityVerificationProvider {

    override fun verify(transactionId: String): IdentityVerificationResult {
        TODO("PASS 실벤더 연동 구현 필요")
    }
}
