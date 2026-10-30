package com.ieum.notification

import com.ieum.events.IeumEvent
import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.events.Topics
import com.ieum.grpc.client.account.AccountClient
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.grpc.client.family.FamilyInfo
import com.ieum.notification.repository.NotificationRepository
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class NotificationReceiverTest @Autowired constructor(
	private val kafkaTemplate: KafkaTemplate<String, String>,
	private val notificationRepository: NotificationRepository,
	private val objectMapper: ObjectMapper,
) : PostgresKafkaTestSupport() {

	@MockitoBean
	private lateinit var accountClient: AccountClient

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val mom = UUID.randomUUID()
	private val dad = UUID.randomUUID()
	private val kid = UUID.randomUUID()
	private val familyId = UUID.randomUUID()
	private val todoId = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		notificationRepository.deleteAllInBatch()
		`when`(accountClient.getNicknames(setOf(mom))).thenReturn(mapOf(mom to "엄마"))
		`when`(familyClient.getFamily(familyId)).thenReturn(FamilyInfo("우리집", emptyMap()))
	}

	private fun event(
		type: NotificationEventType = NotificationEventType.TODO_ASSIGNED,
		recipients: List<UUID> = listOf(dad, kid),
		eventId: UUID = UUID.randomUUID(),
		dedupKey: String? = null,
	) = IeumEvent(
		eventId = eventId,
		eventType = type.name,
		createdAt = LocalDateTime.of(2026, 10, 30, 9, 0),
		data = NotificationEventData(
			recipientAccountIds = recipients,
			familyId = familyId,
			targetType = NotificationTargetType.TODO,
			targetId = todoId,
			actorAccountId = mom,
			subject = "분리수거하기",
			at = LocalDateTime.of(2026, 10, 30, 19, 0),
			amount = null,
			role = null,
			dedupKey = dedupKey,
		),
	)

	private fun send(event: IeumEvent<NotificationEventData>) = send(objectMapper.writeValueAsString(event))

	private fun send(raw: String) {
		kafkaTemplate.send(Topics.NOTIFICATION, raw).get()
	}

	// 리스너는 비동기라 원하는 상태가 될 때까지 짧게 기다린다.
	private fun awaitCount(expected: Long) {
		val deadline = System.currentTimeMillis() + 10_000
		while (notificationRepository.count() != expected && System.currentTimeMillis() < deadline) {
			Thread.sleep(100)
		}
		assertEquals(expected, notificationRepository.count())
	}

	@Test
	fun `받는 사람마다 문구를 만들어 한 건씩 저장한다`() {
		send(event())

		awaitCount(2)
		val saved = notificationRepository.findAll().sortedBy { it.accountId == kid }
		assertEquals(listOf(dad, kid), saved.map { it.accountId })
		saved.forEach {
			assertEquals("엄마님이 할 일을 맡겼어요", it.title)
			assertEquals("분리수거하기 · 10/30(금) 19:00", it.body)
			assertEquals(familyId, it.familyId)
			assertEquals(NotificationTargetType.TODO, it.targetType)
			assertEquals(todoId, it.targetId)
			assertEquals(false, it.isRead)
		}
	}

	@Test
	fun `가족 일정 등록은 가족 이름을 쓴다`() {
		send(event(type = NotificationEventType.FAMILY_SCHEDULE_CREATED, recipients = listOf(dad)))

		awaitCount(1)
		assertEquals("[우리집] 가족 일정", notificationRepository.findAll().single().title)
	}

	@Test
	fun `같은 이벤트가 두 번 와도 한 건이다`() {
		val event = event(recipients = listOf(dad))
		send(event)
		send(event)
		send(event(recipients = listOf(kid)))

		awaitCount(2)
		Thread.sleep(500)
		assertEquals(2, notificationRepository.count())
	}

	@Test
	fun `dedupKey 가 같으면 eventId 가 달라도 한 건이다`() {
		send(event(recipients = listOf(dad), dedupKey = "TODO_DUE_SOON:$todoId:2026-10-30T19:00"))
		send(event(recipients = listOf(dad), dedupKey = "TODO_DUE_SOON:$todoId:2026-10-30T19:00"))
		send(event(recipients = listOf(kid)))

		awaitCount(2)
		Thread.sleep(500)
		assertEquals(2, notificationRepository.count())
	}

	@Test
	fun `깨진 메시지는 DLT 로 가고 다음 메시지는 처리된다`() {
		val broken = "{not json ${UUID.randomUUID()}"
		send(broken)
		send(event(recipients = listOf(dad)))

		awaitCount(1)
		assertTrue(readDlt().contains(broken))
	}

	private fun readDlt(): List<String> =
		KafkaConsumer<String, String>(
			mapOf(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
				ConsumerConfig.GROUP_ID_CONFIG to "dlt-${UUID.randomUUID()}",
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
			),
			StringDeserializer(),
			StringDeserializer(),
		).use { consumer ->
			consumer.subscribe(listOf(Topics.NOTIFICATION_DLT))
			val values = mutableListOf<String>()
			val deadline = System.currentTimeMillis() + 10_000
			while (values.isEmpty() && System.currentTimeMillis() < deadline) {
				consumer.poll(Duration.ofMillis(500)).forEach { values += it.value() }
			}
			values
		}
}
