package com.ieum.grpc.client.family

import java.util.UUID

interface FamilyClient {

	// 구성원이 아니면 null 이다.
	fun getRole(familyId: UUID, accountId: UUID): FamilyRole?

	fun isMember(familyId: UUID, accountId: UUID): Boolean = getRole(familyId, accountId) != null

	// 구성원인 계정만 담는다. 여러 계정을 확인할 때 한 번에 묻는다.
	fun getRoles(familyId: UUID, accountIds: Collection<UUID>): Map<UUID, FamilyRole>

	// 없거나 삭제된 가족이면 null 이다.
	fun getFamily(familyId: UUID): FamilyInfo?
}
