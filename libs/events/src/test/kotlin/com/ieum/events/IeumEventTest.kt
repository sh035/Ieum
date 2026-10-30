package com.ieum.events

import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

class IeumEventTest {

	private val mapper = jacksonObjectMapper()

	private val event = IeumEvent(
		eventId = UUID.fromString("0190f1d2-0000-7000-8000-000000000001"),
		eventType = NotificationEventType.TODO_ASSIGNED.name,
		createdAt = LocalDateTime.of(2026, 10, 30, 9, 0),
		data = NotificationEventData(
			recipientAccountIds = listOf(UUID.fromString("0190f1d2-0000-7000-8000-000000000002")),
			familyId = UUID.fromString("0190f1d2-0000-7000-8000-000000000003"),
			targetType = NotificationTargetType.TODO,
			targetId = UUID.fromString("0190f1d2-0000-7000-8000-000000000004"),
			actorAccountId = UUID.fromString("0190f1d2-0000-7000-8000-000000000005"),
			subject = "분리수거하기",
			at = LocalDateTime.of(2026, 10, 30, 19, 0),
			amount = null,
			role = null,
			dedupKey = null,
		),
	)

	@Test
	fun `envelope 필드는 eventId eventType createdAt data 이다`() {
		val json = mapper.readTree(mapper.writeValueAsString(event))

		assertEquals(setOf("eventId", "eventType", "createdAt", "data"), json.propertyNames().toSet())
		assertEquals("2026-10-30T09:00:00", json.get("createdAt").asString())
	}

	@Test
	fun `직렬화한 이벤트를 그대로 되읽는다`() {
		val read = mapper.readValue<IeumEvent<NotificationEventData>>(mapper.writeValueAsString(event))

		assertEquals(event, read)
	}
}
