package com.vivire.locapet.domain.share

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import java.time.Instant

@MappedSuperclass
abstract class SoftDeletable : ModifiedTraceable() {

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        protected set

    fun markDeleted() {
        this.deletedAt = Instant.now()
    }

    val isDeleted: Boolean
        get() = deletedAt != null
}
