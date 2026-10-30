package com.ieum.notification

import com.ieum.grpc.client.account.AccountClient
import com.ieum.grpc.client.family.FamilyClient
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
class NotificationApplicationTest : PostgresKafkaTestSupport() {

	@MockitoBean
	private lateinit var accountClient: AccountClient

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	// ddl-auto: validate 라서 엔티티와 schema.sql 이 어긋나면 컨텍스트가 뜨지 않는다.
	@Test
	fun `엔티티가 스키마와 일치한다`() {
	}
}
