package com.ieum.family.controller.dto.response

import java.time.LocalDateTime

data class FamilyInvitationResponse(
	val code: String,
	val expiresAt: LocalDateTime,
)
