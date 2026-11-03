package com.ieum.events

import java.util.UUID

data class FamilyEventData(
	val familyId: UUID,
	// FAMILY_MEMBER_LEFT 에서 나가거나 내보내진 계정이다.
	val accountId: UUID? = null,
)
