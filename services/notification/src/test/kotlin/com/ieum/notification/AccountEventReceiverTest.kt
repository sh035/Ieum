package com.ieum.notification

import com.ieum.events.AccountEventData
import com.ieum.events.AccountEventType
import com.ieum.events.IeumEvent
import com.ieum.events.NotificationEventType
import com.ieum.events.Topics
import com.ieum.grpc.client.account.AccountClient
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.notification.domain.entity.DeviceToken
import com.ieum.notification.domain.entity.Notification
import com.ieum.notification.domain.enums.DevicePlatform
import com.ieum.notification.repository.DeviceTokenRepository
import com.ieum.notification.repository.NotificationRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
class AccountEventReceiverTest @Autowired constructor(
	private val kafkaTemplate: KafkaTemplate<String, String>,
	private val objectMapper: ObjectMapper,
	private val notificationRepository: NotificationRepository,
	private val deviceTokenRepository: DeviceTokenRepository,
) : PostgresKafkaTestSupport() {

	@MockitoBean
	private lateinit var accountClient: AccountClient

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val withdrawn = UUID.randomUUID()
	private val other = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		notificationRepository.deleteAllInBatch()
		deviceTokenRepository.deleteAllInBatch()
	}

	private fun notification(accountId: UUID) = notificationRepository.save(
		Notification(
			accountId = accountId,
			familyId = null,
			type = NotificationEventType.TODO_ASSIGNED,
			title = "할 일",
			body = "분리수거하기",
			targetType = null,
			targetId = null,
			dedupKey = UUID.randomUUID().toString(),
		),
	)

	private fun aliveOf(accountId: UUID) =
		notificationRepository.findAll().count { it.accountId == accountId && !it.isDeleted } +
			deviceTokenRepository.findAllByAccountIdAndIsDeletedFalse(accountId).size

	@Test
	fun `탈퇴 이벤트를 받으면 그 계정의 기기 토큰과 알림함만 지운다`() {
		notification(withdrawn)
		notification(withdrawn)
		notification(other)
		deviceTokenRepository.save(DeviceToken(accountId = withdrawn, token = "token-withdrawn", platform = DevicePlatform.ANDROID))
		deviceTokenRepository.save(DeviceToken(accountId = other, token = "token-other", platform = DevicePlatform.ANDROID))

		val event = IeumEvent(UUID.randomUUID(), AccountEventType.ACCOUNT_DELETED.name, LocalDateTime.now(), AccountEventData(withdrawn))
		kafkaTemplate.send(Topics.ACCOUNT, withdrawn.toString(), objectMapper.writeValueAsString(event)).get()

		// 리스너는 비동기라 지워질 때까지 짧게 기다린다.
		val deadline = System.currentTimeMillis() + 10_000
		while (aliveOf(withdrawn) != 0 && System.currentTimeMillis() < deadline) {
			Thread.sleep(100)
		}
		assertEquals(0, aliveOf(withdrawn))
		assertEquals(2, aliveOf(other))
	}
}
