package com.ieum.family.controller.dto.response

import java.util.UUID

data class InvitedFamilyResponse(
	val familyId: UUID,
	val name: String,
)
