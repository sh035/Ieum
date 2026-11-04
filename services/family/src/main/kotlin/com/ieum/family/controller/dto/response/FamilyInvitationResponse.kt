package com.ieum.family.controller.dto.response

import java.time.LocalDateTime
import java.util.UUID

// OWNER 가 보는 보낸 초대다. 받는 사람이 탈퇴했으면 닉네임은 null 이다.
data class FamilyInvitationResponse(
	val familyInvitationId: UUID,
	val inviteeAccountId: UUID,
	val inviteeNickname: String?,
	val expiresAt: LocalDateTime,
)
