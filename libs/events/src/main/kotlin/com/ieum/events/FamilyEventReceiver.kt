package com.ieum.events

import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.UUID

// 가족 이벤트를 받는 서비스는 이 클래스를 상속해 떠남 / 삭제 처리만 구현한다.
// family 의 outbox 는 같은 이벤트를 두 번 보낼 수 있어서 구현은 다시 처리해도 결과가 같아야 한다.
abstract class FamilyEventReceiver(
	private val objectMapper: ObjectMapper,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// family 가 새 이벤트 종류를 먼저 배포해도 막히지 않게 모르는 종류는 건너뛴다.
	@KafkaListener(topics = [Topics.FAMILY])
	fun receive(message: String) {
		val event = objectMapper.readValue<IeumEvent<FamilyEventData>>(message)
		val data = event.data
		log.debug("Family event received. eventType={}, eventId={}, familyId={}", event.eventType, event.eventId, data.familyId)

		when (FamilyEventType.entries.find { it.name == event.eventType }) {
			FamilyEventType.FAMILY_MEMBER_LEFT ->
				onMemberLeft(data.familyId, requireNotNull(data.accountId) { "accountId is required. eventId=${event.eventId}" }, event.createdAt)
			FamilyEventType.FAMILY_DELETED -> onFamilyDeleted(data.familyId)
			null -> log.info("Unknown family event skipped. eventType={}, eventId={}", event.eventType, event.eventId)
		}
	}

	protected abstract fun onMemberLeft(familyId: UUID, accountId: UUID, leftAt: LocalDateTime)

	protected abstract fun onFamilyDeleted(familyId: UUID)
}
