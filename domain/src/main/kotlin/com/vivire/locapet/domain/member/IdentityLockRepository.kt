package com.vivire.locapet.domain.member

import org.springframework.data.jpa.repository.JpaRepository

interface IdentityLockRepository : JpaRepository<IdentityLock, String>
