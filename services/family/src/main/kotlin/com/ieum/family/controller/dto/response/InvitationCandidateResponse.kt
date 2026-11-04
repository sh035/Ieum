package com.ieum.family.controller.dto.response

import java.util.UUID

data class InvitationCandidateResponse(
	val accountId: UUID,
	val nickname: String,
)
