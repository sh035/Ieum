package com.ieum.expense

import com.ieum.expense.domain.enums.FamilyRole
import com.ieum.expense.service.FamilyClient
import com.ieum.web.exception.IeumException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RestClientTest(FamilyClient::class, properties = ["family.url=http://family"])
class FamilyClientTest @Autowired constructor(
	private val familyClient: FamilyClient,
	private val server: MockRestServiceServer,
) {

	private val familyId = UUID.randomUUID()
	private val accountId = UUID.randomUUID()
	private val uri = "http://family/internal/family/$familyId/member/$accountId/role"

	private fun withRole(role: String) =
		withSuccess("""{"status":200,"data":{"role":"$role"}}""", MediaType.APPLICATION_JSON)

	@Test
	fun `200이면 응답의 역할을 돌려주고 구성원이다`() {
		server.expect(requestTo(uri)).andRespond(withRole("MANAGER"))
		server.expect(requestTo(uri)).andRespond(withRole("MEMBER"))

		assertEquals(FamilyRole.MANAGER, familyClient.getRole(familyId, accountId))
		assertTrue(familyClient.isMember(familyId, accountId))
	}

	@Test
	fun `404면 역할 없음이고 구성원 아님`() {
		server.expect(requestTo(uri)).andRespond(withResourceNotFound())
		server.expect(requestTo(uri)).andRespond(withResourceNotFound())

		assertNull(familyClient.getRole(familyId, accountId))
		assertFalse(familyClient.isMember(familyId, accountId))
	}

	@Test
	fun `family 오류, 연결 실패, 본문 없음은 503`() {
		server.expect(requestTo(uri)).andRespond(withServerError())
		server.expect(requestTo(uri)).andRespond { throw IOException("connection refused") }
		server.expect(requestTo(uri)).andRespond(withSuccess())

		repeat(3) {
			val e = assertFailsWith<IeumException> { familyClient.getRole(familyId, accountId) }
			assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.errorCode.status)
		}
	}
}
