package com.ieum.grpc.client.family

import com.ieum.web.exception.ErrorCode
import com.ieum.web.exception.IeumException
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger(FamilyClient::class.java)

interface FamilyClient {

	// 구성원이 아니면 null 이다.
	fun getRole(familyId: UUID, accountId: UUID): FamilyRole?

	fun isMember(familyId: UUID, accountId: UUID): Boolean = getRole(familyId, accountId) != null

	// 구성원인 계정만 담는다. 여러 계정을 확인할 때 한 번에 묻는다.
	fun getRoles(familyId: UUID, accountIds: Collection<UUID>): Map<UUID, FamilyRole>

	// 없거나 삭제된 가족이면 null 이다.
	fun getFamily(familyId: UUID): FamilyInfo?

	// 계정이 지금 속한 가족들이다.
	fun getFamilyIds(accountId: UUID): List<UUID>

	// 그 가족에서 쓰는 닉네임이다. 나간 구성원은 마지막 닉네임이고, 구성원이었던 적 없는 계정은 빠진다.
	fun getNicknames(familyId: UUID, accountIds: Collection<UUID>): Map<UUID, String>
}

// 구성원이 아니면 서비스가 정한 오류로 막는다.
// 테스트의 FamilyClient mock 은 isMember 만 정하므로, mock 이 가로채지 않게 인터페이스 메서드가 아닌 확장 함수로 둔다.
fun FamilyClient.requireMember(familyId: UUID, accountId: UUID, errorCode: ErrorCode) {
	if (!isMember(familyId, accountId)) {
		throw IeumException(errorCode)
	}
}

// 가족별 현재 구성원이다. 없는 가족은 빈 구성원이다.
// 가족마다 family 를 한 번씩 부른다. 가족이 많아져 호출하는 쪽 deadline 을 넘기면 family 에 일괄 조회를 추가한다.
fun FamilyClient.getMembersOf(familyIds: Collection<UUID>): Map<UUID, Set<UUID>> =
	familyIds.distinct().associateWith { familyId ->
		getFamily(familyId)?.members?.keys
			?: emptySet<UUID>().also { log.warn("Family not found. familyId={}", familyId) }
	}
