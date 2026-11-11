package com.ieum.family.controller.dto.response

import java.util.UUID

data class WithdrawalCheckResponse(
	// 다른 구성원이 있는데 OWNER 인 가족. 소유권을 넘겨야 탈퇴할 수 있다.
	val transferRequiredFamilies: List<FamilySummaryResponse>,
	// 혼자인 가족. 탈퇴하면 삭제된다.
	val soloFamilies: List<FamilySummaryResponse>,
)

data class FamilySummaryResponse(
	val familyId: UUID,
	val name: String,
)
