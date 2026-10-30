package com.ieum.notification

import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.grpc.client.account.AccountClient
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.notification.domain.entity.DeviceToken
import com.ieum.notification.domain.entity.Notification
import com.ieum.notification.domain.enums.DevicePlatform
import com.ieum.notification.repository.DeviceTokenRepository
import com.ieum.notification.repository.NotificationRepository
import com.ieum.notification.service.delivery.FcmClient
import com.ieum.notification.service.delivery.FcmNotificationSender
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest(properties = ["firebase.credentials=not-used"])
class FcmNotificationSenderTest @Autowired constructor(
	private val fcmNotificationSender: FcmNotificationSender,
	private val notificationRepository: NotificationRepository,
	private val deviceTokenRepository: DeviceTokenRepository,
) : PostgresKafkaTestSupport() {

	@MockitoBean
	private lateinit var accountClient: AccountClient

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	@MockitoBean
	private lateinit var fcmClient: FcmClient

	private val me = UUID.randomUUID()
	private val other = UUID.randomUUID()
	private val todoId = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		notificationRepository.deleteAllInBatch()
		deviceTokenRepository.deleteAllInBatch()
	}

	private fun notification(accountId: UUID = me) = notificationRepository.save(
		Notification(
			accountId = accountId,
			familyId = null,
			type = NotificationEventType.TODO_ASSIGNED,
			title = "엄마님이 할 일을 맡겼어요",
			body = "분리수거하기",
			targetType = NotificationTargetType.TODO,
			targetId = todoId,
			dedupKey = UUID.randomUUID().toString(),
		),
	)

	private fun token(token: String, accountId: UUID = me) =
		deviceTokenRepository.save(DeviceToken(accountId = accountId, token = token, platform = DevicePlatform.ANDROID))

	@Test
	fun `내 기기 토큰으로 보내고 무효 토큰만 지운다`() {
		token("phone")
		token("old-phone")
		token("other-phone", other)
		token("logged-out").apply { delete() }.also(deviceTokenRepository::save)
		val notification = notification()
		val data = mapOf(
			"notificationId" to notification.notificationId.toString(),
			"type" to "TODO_ASSIGNED",
			"targetType" to "TODO",
			"targetId" to todoId.toString(),
		)
		`when`(fcmClient.send(setOf("phone", "old-phone"), "엄마님이 할 일을 맡겼어요", "분리수거하기", data)).thenReturn(setOf("old-phone"))

		fcmNotificationSender.send(notification)

		verify(fcmClient).send(setOf("phone", "old-phone"), "엄마님이 할 일을 맡겼어요", "분리수거하기", data)
		assertEquals(
			setOf("phone", "other-phone"),
			deviceTokenRepository.findAll().filterNot { it.isDeleted }.map { it.token }.toSet(),
		)
	}

	@Test
	fun `등록한 기기가 없으면 보내지 않는다`() {
		token("other-phone", other)

		fcmNotificationSender.send(notification())

		verifyNoInteractions(fcmClient)
	}
}
