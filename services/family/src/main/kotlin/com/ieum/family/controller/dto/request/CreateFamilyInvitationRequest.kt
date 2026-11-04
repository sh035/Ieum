package com.ieum.family.controller.dto.request

import java.util.UUID

data class CreateFamilyInvitationRequest(
	val inviteeAccountId: UUID,
)
