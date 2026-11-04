package com.ieum.family.controller.dto.response

import java.time.LocalDateTime
import java.util.UUID

// 받는 사람이 보는 초대다. 초대한 사람이 탈퇴했으면 닉네임은 null 이다.
data class ReceivedFamilyInvitationResponse(
	val familyInvitationId: UUID,
	val familyId: UUID,
	val familyName: String,
	val inviterAccountId: UUID,
	val inviterNickname: String?,
	val expiresAt: LocalDateTime,
)
