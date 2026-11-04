package com.ieum.grocery.service

import com.ieum.events.FamilyEventData
import com.ieum.events.FamilyEventType
import com.ieum.events.IeumEvent
import com.ieum.events.Topics
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Component
class FamilyEventReceiver(
	private val objectMapper: ObjectMapper,
	private val familyEventService: FamilyEventService,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// family 가 새 이벤트 종류를 먼저 배포해도 막히지 않게 모르는 종류는 건너뛴다.
	@KafkaListener(topics = [Topics.FAMILY])
	fun receive(message: String) {
		val event = objectMapper.readValue<IeumEvent<FamilyEventData>>(message)
		val data = event.data

		when (FamilyEventType.entries.find { it.name == event.eventType }) {
			FamilyEventType.FAMILY_MEMBER_LEFT ->
				familyEventService.openPrivateItems(data.familyId, requireNotNull(data.accountId) { "accountId is required. eventId=${event.eventId}" }, event.createdAt)
			FamilyEventType.FAMILY_DELETED -> familyEventService.deleteFamily(data.familyId)
			null -> log.info("Unknown family event skipped. eventType={}, eventId={}", event.eventType, event.eventId)
		}
	}
}
