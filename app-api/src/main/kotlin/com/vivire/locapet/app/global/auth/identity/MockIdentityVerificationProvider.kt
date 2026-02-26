package com.vivire.locapet.app.global.auth.identity

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
@ConditionalOnProperty(name = ["app.auth.identity-verification.provider"], havingValue = "mock")
class MockIdentityVerificationProvider : IdentityVerificationProvider {

    override fun verify(transactionId: String) = IdentityVerificationResult(
        ci = "MOCK_CI_$transactionId",
        phone = "010-0000-0000",
        name = "테스트유저",
        birthDate = LocalDate.of(1990, 1, 1)
    )
}
