package com.ieum.notification

import com.ieum.grpc.client.account.AccountClient
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.notification.service.delivery.FcmNotificationSender
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertEquals

@SpringBootTest
class NotificationApplicationTest @Autowired constructor(
	private val context: ApplicationContext,
) : PostgresKafkaTestSupport() {

	@MockitoBean
	private lateinit var accountClient: AccountClient

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	// ddl-auto: validate 라서 엔티티와 schema.sql 이 어긋나면 컨텍스트가 뜨지 않는다.
	@Test
	fun `엔티티가 스키마와 일치한다`() {
	}

	@Test
	fun `Firebase 키가 없으면 FCM 전송을 끈다`() {
		assertEquals(emptyMap(), context.getBeansOfType(FcmNotificationSender::class.java))
	}
}
