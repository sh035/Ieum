package com.ieum.account.controller.dto.response

import java.util.UUID

data class WithdrawalCheckResponse(
	// 다른 구성원이 있는데 OWNER 인 가족. 소유권을 넘겨야 탈퇴할 수 있다.
	val transferRequiredFamilies: List<WithdrawalFamilyResponse>,
	// 혼자인 가족. 탈퇴하면 삭제된다.
	val soloFamilies: List<WithdrawalFamilyResponse>,
)

data class WithdrawalFamilyResponse(
	val familyId: UUID,
	val name: String,
)
