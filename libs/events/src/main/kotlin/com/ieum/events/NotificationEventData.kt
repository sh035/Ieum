package com.ieum.events

import java.time.LocalDateTime
import java.util.UUID

// 받는 사람과 문구에 필요한 값은 발행하는 서비스가 채운다. notification 은 도메인 규칙을 모른다.
data class NotificationEventData(
	val recipientAccountIds: List<UUID>,
	// 알림함 가족 탭 기준이다. 가족과 무관한 알림이면 null 이다.
	val familyId: UUID? = null,
	val targetType: NotificationTargetType? = null,
	val targetId: UUID? = null,
	// 문구에 넣을 닉네임을 조회할 계정이다.
	val actorAccountId: UUID? = null,
	val subject: String? = null,
	// 날짜만 있으면 자정으로 채운다.
	val at: LocalDateTime? = null,
	val amount: Int? = null,
	// FAMILY_ROLE_CHANGED 의 새 역할이다.
	val role: String? = null,
	// 스케줄러처럼 같은 알림을 여러 번 발행할 수 있으면 채운다. 없으면 eventId 로 중복을 막는다.
	val dedupKey: String? = null,
)
