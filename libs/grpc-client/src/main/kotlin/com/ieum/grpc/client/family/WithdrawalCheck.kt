package com.ieum.grpc.client.family

import java.util.UUID

data class WithdrawalCheck(
	// 다른 구성원이 있는데 OWNER 인 가족. 소유권을 넘겨야 탈퇴할 수 있다.
	val transferRequiredFamilies: List<FamilySummary>,
	// 혼자인 가족. 탈퇴하면 삭제된다.
	val soloFamilies: List<FamilySummary>,
)

data class FamilySummary(
	val familyId: UUID,
	val name: String,
)

data class LeaveAllFamiliesResult(
	val left: Boolean,
	val check: WithdrawalCheck,
)
