package com.vivire.locapet.domain.member

import org.springframework.data.jpa.repository.JpaRepository

interface IdentityVerificationRepository : JpaRepository<IdentityVerification, Long>
