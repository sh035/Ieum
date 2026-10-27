package com.ieum.family.controller.dto.response

import com.ieum.family.domain.enums.FamilyRole
import java.util.UUID

data class FamilyResponse(
	val familyId: UUID,
	val name: String,
	val role: FamilyRole,
)
