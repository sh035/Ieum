package com.ieum.grpc.client.family

import com.ieum.web.exception.ErrorCode
import com.ieum.web.exception.IeumException
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

// 구성원이 아니면 서비스가 정한 오류로 막는다.
// 테스트의 FamilyClient mock 은 isMember 만 정하므로, mock 이 가로채지 않게 인터페이스 메서드가 아닌 확장 함수로 둔다.
fun FamilyClient.requireMember(familyId: UUID, accountId: UUID, errorCode: ErrorCode) {
	if (!isMember(familyId, accountId)) {
		throw IeumException(errorCode)
	}
}
