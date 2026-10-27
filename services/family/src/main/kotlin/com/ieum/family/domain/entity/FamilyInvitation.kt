package com.ieum.family.domain.entity

import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "family_invitation")
class FamilyInvitation(
	@Column(nullable = false, updatable = false)
	val familyId: UUID,

	@Column(nullable = false, updatable = false)
	val code: String,

	@Column(nullable = false, updatable = false)
	val expiresAt: LocalDateTime,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val familyInvitationId: UUID? = null,
) : BaseEntity() {

	fun isExpired(): Boolean = !expiresAt.isAfter(LocalDateTime.now())
}
