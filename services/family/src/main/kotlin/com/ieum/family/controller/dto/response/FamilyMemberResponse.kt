package com.ieum.family.controller.dto.response

import com.ieum.family.domain.enums.FamilyRole
import java.time.LocalDateTime
import java.util.UUID

data class FamilyMemberResponse(
	val familyMemberId: UUID,
	val accountId: UUID,
	val role: FamilyRole,
	val joinedAt: LocalDateTime,
)
