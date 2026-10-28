package com.ieum.grocery

import com.ieum.grocery.service.FamilyClient
import com.ieum.web.exception.IeumException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.io.IOException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RestClientTest(FamilyClient::class, properties = ["family.url=http://family"])
class FamilyClientTest @Autowired constructor(
	private val familyClient: FamilyClient,
	private val server: MockRestServiceServer,
) {

	private val familyId = UUID.randomUUID()
	private val accountId = UUID.randomUUID()
	private val uri = "http://family/internal/family/$familyId/member/$accountId/role"

	@Test
	fun `200이면 구성원`() {
		server.expect(requestTo(uri)).andRespond(withSuccess())

		assertTrue(familyClient.isMember(familyId, accountId))
	}

	@Test
	fun `404면 구성원 아님`() {
		server.expect(requestTo(uri)).andRespond(withResourceNotFound())

		assertFalse(familyClient.isMember(familyId, accountId))
	}

	@Test
	fun `family 오류나 연결 실패는 503`() {
		server.expect(requestTo(uri)).andRespond(withServerError())
		server.expect(requestTo(uri)).andRespond { throw IOException("connection refused") }

		repeat(2) {
			val e = assertFailsWith<IeumException> { familyClient.isMember(familyId, accountId) }
			assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.errorCode.status)
		}
	}
}
