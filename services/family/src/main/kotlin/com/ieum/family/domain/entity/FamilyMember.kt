package com.ieum.family.domain.entity

import com.ieum.family.domain.enums.FamilyRole
import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.util.UUID

@Entity
@Table(name = "family_member")
class FamilyMember(
	@Column(nullable = false, updatable = false)
	val familyId: UUID,

	@Column(nullable = false, updatable = false)
	val accountId: UUID,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var role: FamilyRole,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val familyMemberId: UUID? = null,
) : BaseEntity()
