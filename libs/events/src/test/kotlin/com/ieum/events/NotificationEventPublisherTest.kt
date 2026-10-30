package com.ieum.events

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringJUnitConfig(NotificationEventPublisherTest.Config::class)
class NotificationEventPublisherTest @Autowired constructor(
	private val publisher: NotificationEventPublisher,
	private val kafkaTemplate: KafkaTemplate<String, String>,
	private val transactionManager: PlatformTransactionManager,
) {

	// DB 없이 커밋 / 롤백 콜백만 돌리는 트랜잭션 매니저다.
	class NoOpTransactionManager : AbstractPlatformTransactionManager() {
		override fun doGetTransaction() = Any()
		override fun doBegin(transaction: Any, definition: org.springframework.transaction.TransactionDefinition) {}
		override fun doCommit(status: DefaultTransactionStatus) {}
		override fun doRollback(status: DefaultTransactionStatus) {}
	}

	@Configuration
	@EnableTransactionManagement
	@Import(NotificationEventPublisher::class, NotificationEventRelay::class)
	class Config {
		@Bean
		fun transactionManager() = NoOpTransactionManager()

		@Suppress("UNCHECKED_CAST")
		@Bean
		fun kafkaTemplate(): KafkaTemplate<String, String> = mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>

		@Bean
		fun objectMapper(): ObjectMapper = jacksonObjectMapper()
	}

	private val mom = UUID.randomUUID()
	private val dad = UUID.randomUUID()
	private val todoId = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		reset(kafkaTemplate)
	}

	private fun data(recipients: List<UUID>) = NotificationEventData(
		recipientAccountIds = recipients,
		targetType = NotificationTargetType.TODO,
		targetId = todoId,
		actorAccountId = mom,
		subject = "분리수거하기",
		at = LocalDateTime.of(2026, 10, 30, 19, 0),
	)

	private fun inTransaction(rollback: Boolean = false, block: () -> Unit) =
		TransactionTemplate(transactionManager).executeWithoutResult { status ->
			block()
			if (rollback) status.setRollbackOnly()
		}

	@Test
	fun `커밋 후 행동한 본인을 뺀 받는 사람으로 전송한다`() {
		inTransaction { publisher.publish(NotificationEventType.TODO_ASSIGNED, data(listOf(dad, mom, dad))) }

		val json = ArgumentCaptor.forClass(String::class.java)
		verify(kafkaTemplate).send(eq(Topics.NOTIFICATION), eq(todoId.toString()), json.capture())
		val event = jacksonObjectMapper().readValue<IeumEvent<NotificationEventData>>(json.value)
		assertEquals("TODO_ASSIGNED", event.eventType)
		assertEquals(data(listOf(dad)), event.data)
	}

	@Test
	fun `롤백되면 전송하지 않는다`() {
		inTransaction(rollback = true) { publisher.publish(NotificationEventType.TODO_ASSIGNED, data(listOf(dad))) }

		verifyNoInteractions(kafkaTemplate)
	}

	@Test
	fun `받는 사람이 본인뿐이면 발행하지 않는다`() {
		inTransaction { publisher.publish(NotificationEventType.TODO_ASSIGNED, data(listOf(mom))) }

		verifyNoInteractions(kafkaTemplate)
	}

	@Test
	fun `전송이 실패해도 커밋된 요청은 깨지지 않는다`() {
		@Suppress("UNCHECKED_CAST")
		val failing = mock(KafkaTemplate::class.java) { throw IllegalStateException("kafka down") } as KafkaTemplate<String, String>

		NotificationEventRelay(failing, jacksonObjectMapper()).relay(NotificationEvent(NotificationEventType.TODO_ASSIGNED, data(listOf(dad))))
	}

	@Test
	fun `트랜잭션 밖에서 발행하면 이벤트가 사라지지 않게 바로 실패한다`() {
		assertFailsWith<IllegalStateException> { publisher.publish(NotificationEventType.TODO_ASSIGNED, data(listOf(dad))) }
	}
}
