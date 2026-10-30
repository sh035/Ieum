package com.ieum.notification

import com.ieum.events.IeumEvent
import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.events.Topics
import com.ieum.grpc.client.account.AccountClient
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.notification.repository.NotificationRepository
import com.ieum.notification.service.delivery.SseEmitterRegistry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 구독이 붙는 순간과 발행이 겹쳐 메시지를 건너뛰지 않게 테스트에서는 처음부터 읽는다.
@SpringBootTest(properties = ["notification.delivery.auto-offset-reset=earliest"])
@AutoConfigureMockMvc
class NotificationStreamTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val kafkaTemplate: KafkaTemplate<String, String>,
	private val objectMapper: ObjectMapper,
	private val notificationRepository: NotificationRepository,
	private val listenerRegistry: KafkaListenerEndpointRegistry,
	private val sseEmitterRegistry: SseEmitterRegistry,
) : PostgresKafkaTestSupport() {

	@MockitoBean
	private lateinit var accountClient: AccountClient

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val mom = UUID.randomUUID()
	private val dad = UUID.randomUUID()
	private val kid = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		notificationRepository.deleteAllInBatch()
		`when`(accountClient.getNicknames(setOf(mom))).thenReturn(mapOf(mom to "엄마"))
		ContainerTestUtils.waitForAssignment(requireNotNull(listenerRegistry.getListenerContainer("sse-delivery")), 1)
	}

	private fun connect(accountId: UUID): MvcResult =
		mockMvc.get("/api/v1/notification/stream") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			accept(MediaType.TEXT_EVENT_STREAM)
		}.andExpect { request { asyncStarted() } }.andReturn()

	private fun MvcResult.body() = response.getContentAsString(Charsets.UTF_8)

	private fun awaitBody(result: MvcResult, text: String) {
		val deadline = System.currentTimeMillis() + 10_000
		while (!result.body().contains(text) && System.currentTimeMillis() < deadline) {
			Thread.sleep(100)
		}
		assertTrue(result.body().contains(text), result.body())
	}

	@Test
	fun `연결한 내 기기마다 알림을 보내고 다른 계정에는 보내지 않는다`() {
		val phone = connect(dad)
		val tablet = connect(dad)
		val other = connect(kid)

		val event = IeumEvent(
			eventId = UUID.randomUUID(),
			eventType = NotificationEventType.TODO_ASSIGNED.name,
			createdAt = LocalDateTime.of(2026, 10, 30, 9, 0),
			data = NotificationEventData(
				recipientAccountIds = listOf(dad),
				familyId = null,
				targetType = NotificationTargetType.TODO,
				targetId = UUID.randomUUID(),
				actorAccountId = mom,
				subject = "분리수거하기",
				at = null,
				amount = null,
				role = null,
				dedupKey = null,
			),
		)
		kafkaTemplate.send(Topics.NOTIFICATION, objectMapper.writeValueAsString(event)).get()

		awaitBody(phone, "엄마님이 할 일을 맡겼어요")
		awaitBody(tablet, "엄마님이 할 일을 맡겼어요")
		val notificationId = notificationRepository.findAll().single().notificationId
		assertTrue(phone.body().contains("event:notification"))
		assertTrue(phone.body().contains("\"notificationId\":\"$notificationId\""))
		assertFalse(other.body().contains("event:notification"))
	}

	@Test
	fun `끊긴 연결로 보내다 실패해도 예외가 밖으로 나가지 않는다`() {
		val accountId = UUID.randomUUID()
		sseEmitterRegistry.connect(accountId).complete()

		sseEmitterRegistry.send(accountId, "notification", "hello")
		sseEmitterRegistry.send(accountId, "notification", "hello")
	}

	@Test
	fun `서버가 내려가면 열린 연결을 먼저 닫는다`() {
		val emitter = sseEmitterRegistry.connect(UUID.randomUUID())

		sseEmitterRegistry.completeAll()

		assertFailsWith<IllegalStateException> { emitter.send("after shutdown") }
	}

	@Test
	fun `깨진 전달 메시지는 DLT 로 보내지 않고 버린다`() {
		val broken = "{broken delivery ${UUID.randomUUID()}"
		kafkaTemplate.send(Topics.NOTIFICATION_DELIVERY, broken).get()
		val phone = connect(dad)
		val delivery = """{"accountId":"$dad","notification":{"notificationId":"${UUID.randomUUID()}","familyId":null,""" +
			""""type":"TODO_COMPLETED","title":"다음 알림","body":"분리수거하기","targetType":null,"targetId":null,""" +
			""""isRead":false,"createdAt":"2026-10-30T09:00:00"}}"""
		kafkaTemplate.send(Topics.NOTIFICATION_DELIVERY, delivery).get()

		awaitBody(phone, "다음 알림")
		assertFalse(readAllDlt().any { it.contains(broken) })
	}

	// DLT 를 처음부터 끝까지 읽는다. 빈 poll 이 두 번 이어지면 끝으로 본다.
	private fun readAllDlt(): List<String> =
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
			var emptyPolls = 0
			while (emptyPolls < 2) {
				val records = consumer.poll(Duration.ofSeconds(2))
				if (records.isEmpty) emptyPolls++ else emptyPolls = 0
				records.forEach { values += it.value() }
			}
			values
		}
}
