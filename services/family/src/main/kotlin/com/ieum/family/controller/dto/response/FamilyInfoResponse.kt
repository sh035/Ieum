package com.ieum.family.controller.dto.response

import com.ieum.family.domain.enums.FamilyRole
import java.util.UUID

data class FamilyInfoResponse(
	val name: String,
	val members: Map<UUID, FamilyRole>,
)
