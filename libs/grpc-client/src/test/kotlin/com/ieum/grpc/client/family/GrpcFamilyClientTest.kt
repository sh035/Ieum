package com.ieum.grpc.client.family

import com.ieum.proto.family.v1.FamilyInternalServiceGrpc
import com.ieum.proto.family.v1.GetFamilyRequest
import com.ieum.proto.family.v1.GetFamilyResponse
import com.ieum.proto.family.v1.GetMemberRoleRequest
import com.ieum.proto.family.v1.GetMemberRoleResponse
import com.ieum.proto.family.v1.GetMemberRolesRequest
import com.ieum.proto.family.v1.GetMemberRolesResponse
import com.ieum.web.exception.IeumException
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.grpc.client.ChannelBuilderOptions
import org.springframework.grpc.client.GrpcChannelFactory
import org.springframework.http.HttpStatus
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.ieum.proto.family.v1.FamilyMember as ProtoFamilyMember
import com.ieum.proto.family.v1.FamilyRole as ProtoFamilyRole

class GrpcFamilyClientTest {

	private val familyId = UUID.randomUUID()
	private val accountId = UUID.randomUUID()
	private val name = InProcessServerBuilder.generateName()
	private var server: Server? = null
	private var channel: ManagedChannel? = null

	@AfterEach
	fun tearDown() {
		channel?.shutdownNow()
		server?.shutdownNow()
	}

	// family 응답을 흉내 내는 in-process 서버에 붙은 클라이언트를 만든다.
	private fun clientRespondingWith(
		rolesRespond: (GetMemberRolesRequest, StreamObserver<GetMemberRolesResponse>) -> Unit = { _, _ -> },
		familyRespond: (StreamObserver<GetFamilyResponse>) -> Unit = { },
		respond: (StreamObserver<GetMemberRoleResponse>) -> Unit = { },
	): GrpcFamilyClient {
		server = InProcessServerBuilder.forName(name)
			.addService(object : FamilyInternalServiceGrpc.FamilyInternalServiceImplBase() {
				override fun getMemberRole(request: GetMemberRoleRequest, observer: StreamObserver<GetMemberRoleResponse>) {
					respond(observer)
				}

				override fun getMemberRoles(request: GetMemberRolesRequest, observer: StreamObserver<GetMemberRolesResponse>) {
					rolesRespond(request, observer)
				}

				override fun getFamily(request: GetFamilyRequest, observer: StreamObserver<GetFamilyResponse>) {
					familyRespond(observer)
				}
			})
			.build()
			.start()
		val channel = InProcessChannelBuilder.forName(name).build().also { channel = it }

		return GrpcFamilyClient(object : GrpcChannelFactory {
			override fun supports(target: String) = true
			override fun createChannel(target: String, options: ChannelBuilderOptions) = channel
		})
	}

	private fun role(role: ProtoFamilyRole): (StreamObserver<GetMemberRoleResponse>) -> Unit = {
		it.onNext(GetMemberRoleResponse.newBuilder().setRole(role).build())
		it.onCompleted()
	}

	private fun assertUnavailable(client: GrpcFamilyClient) {
		val e = assertFailsWith<IeumException> { client.getRole(familyId, accountId) }
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.errorCode.status)
	}

	@Test
	fun `역할을 받으면 도메인 역할로 바꾸고 구성원이다`() {
		val client = clientRespondingWith(respond = role(ProtoFamilyRole.FAMILY_ROLE_MANAGER))

		assertEquals(FamilyRole.MANAGER, client.getRole(familyId, accountId))
		assertTrue(client.isMember(familyId, accountId))
	}

	@Test
	fun `NOT_FOUND면 역할 없음이고 구성원 아님`() {
		val client = clientRespondingWith(respond = { it.onError(Status.NOT_FOUND.asRuntimeException()) })

		assertNull(client.getRole(familyId, accountId))
		assertFalse(client.isMember(familyId, accountId))
	}

	@Test
	fun `family 오류나 알 수 없는 역할이면 503`() {
		assertUnavailable(clientRespondingWith(respond = { it.onError(Status.INTERNAL.asRuntimeException()) }))
		tearDown()
		assertUnavailable(clientRespondingWith(respond = role(ProtoFamilyRole.FAMILY_ROLE_UNSPECIFIED)))
	}

	@Test
	fun `family가 꺼져 있으면 503`() {
		val client = clientRespondingWith(respond = role(ProtoFamilyRole.FAMILY_ROLE_OWNER))
		server?.shutdownNow()

		assertUnavailable(client)
	}

	@Test
	@Timeout(10, unit = TimeUnit.SECONDS)
	fun `family가 응답하지 않으면 deadline 후 503`() {
		assertUnavailable(clientRespondingWith(respond = { }))
	}

	@Test
	fun `여러 계정을 한 번에 물으면 구성원만 도메인 역할로 돌려준다`() {
		val other = UUID.randomUUID()
		var asked = listOf<String>()
		val client = clientRespondingWith(rolesRespond = { request, observer ->
			asked = request.accountIdsList
			observer.onNext(GetMemberRolesResponse.newBuilder().putRoles(accountId.toString(), ProtoFamilyRole.FAMILY_ROLE_OWNER).build())
			observer.onCompleted()
		})

		assertEquals(mapOf(accountId to FamilyRole.OWNER), client.getRoles(familyId, listOf(accountId, other)))
		assertEquals(listOf(accountId.toString(), other.toString()), asked)
	}

	@Test
	fun `빈 목록은 family를 부르지 않고 일괄 조회 실패는 503`() {
		var called = false
		val client = clientRespondingWith(rolesRespond = { _, observer ->
			called = true
			observer.onError(Status.UNAVAILABLE.asRuntimeException())
		})

		assertEquals(emptyMap(), client.getRoles(familyId, emptyList()))
		assertFalse(called)
		val e = assertFailsWith<IeumException> { client.getRoles(familyId, listOf(accountId)) }
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.errorCode.status)
	}

	@Test
	fun `가족 정보를 도메인 타입으로 바꾼다`() {
		val other = UUID.randomUUID()
		val client = clientRespondingWith(familyRespond = {
			it.onNext(
				GetFamilyResponse.newBuilder()
					.setName("우리집")
					.addMembers(ProtoFamilyMember.newBuilder().setAccountId(accountId.toString()).setRole(ProtoFamilyRole.FAMILY_ROLE_OWNER))
					.addMembers(ProtoFamilyMember.newBuilder().setAccountId(other.toString()).setRole(ProtoFamilyRole.FAMILY_ROLE_MEMBER))
					.build(),
			)
			it.onCompleted()
		})

		assertEquals(FamilyInfo("우리집", mapOf(accountId to FamilyRole.OWNER, other to FamilyRole.MEMBER)), client.getFamily(familyId))
	}

	@Test
	fun `없는 가족은 null 이고 가족 조회 실패는 503`() {
		assertNull(clientRespondingWith(familyRespond = { it.onError(Status.NOT_FOUND.asRuntimeException()) }).getFamily(familyId))
		tearDown()
		val client = clientRespondingWith(familyRespond = { it.onError(Status.INTERNAL.asRuntimeException()) })
		val e = assertFailsWith<IeumException> { client.getFamily(familyId) }
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.errorCode.status)
	}
}
