package com.ieum.family

import com.ieum.family.domain.entity.Family
import com.ieum.family.domain.entity.FamilyMember
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.repository.FamilyInvitationRepository
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.family.repository.FamilyRepository
import com.ieum.proto.family.v1.FamilyInternalServiceGrpc
import com.ieum.proto.family.v1.GetMemberRoleRequest
import com.ieum.proto.family.v1.GetMemberRolesRequest
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.grpc.client.GrpcChannelFactory
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import com.ieum.proto.family.v1.FamilyRole as ProtoFamilyRole

@SpringBootTest
@AutoConfigureTestGrpcTransport
class FamilyInternalGrpcControllerTest @Autowired constructor(
	channels: GrpcChannelFactory,
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val familyInvitationRepository: FamilyInvitationRepository,
) : PostgresTestSupport() {

	private val stub = FamilyInternalServiceGrpc.newBlockingStub(channels.createChannel("family"))
	private val ownerId = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		familyInvitationRepository.deleteAll()
		familyMemberRepository.deleteAll()
		familyRepository.deleteAll()
	}

	private fun createFamily(): UUID {
		val familyId = requireNotNull(familyRepository.save(Family(name = "우리집")).familyId)
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = ownerId, role = FamilyRole.OWNER))
		return familyId
	}

	private fun getRole(familyId: Any, accountId: Any) =
		stub.getMemberRole(
			GetMemberRoleRequest.newBuilder().setFamilyId(familyId.toString()).setAccountId(accountId.toString()).build(),
		)

	private fun assertStatus(code: Status.Code, call: () -> Unit) =
		assertEquals(code, assertFailsWith<StatusRuntimeException> { call() }.status.code)

	@Test
	fun `구성원이면 역할을 반환한다`() {
		val familyId = createFamily()
		val manager = familyMemberRepository.save(
			FamilyMember(familyId = familyId, accountId = UUID.randomUUID(), role = FamilyRole.MANAGER),
		)

		assertEquals(ProtoFamilyRole.FAMILY_ROLE_OWNER, getRole(familyId, ownerId).role)
		assertEquals(ProtoFamilyRole.FAMILY_ROLE_MANAGER, getRole(familyId, manager.accountId).role)
	}

	@Test
	fun `구성원이 아니면 NOT_FOUND`() {
		val familyId = createFamily()

		assertStatus(Status.Code.NOT_FOUND) { getRole(familyId, UUID.randomUUID()) }
	}

	@Test
	fun `내보낸 구성원이면 NOT_FOUND`() {
		val familyId = createFamily()
		val member = familyMemberRepository.save(
			FamilyMember(familyId = familyId, accountId = UUID.randomUUID(), role = FamilyRole.MEMBER),
		)
		member.delete()
		familyMemberRepository.save(member)

		assertStatus(Status.Code.NOT_FOUND) { getRole(familyId, member.accountId) }
	}

	@Test
	fun `UUID 형식이 아니면 INVALID_ARGUMENT`() {
		assertStatus(Status.Code.INVALID_ARGUMENT) { getRole("not-a-uuid", ownerId) }
		assertStatus(Status.Code.INVALID_ARGUMENT) { getRoles(createFamily(), ownerId, "not-a-uuid") }
	}

	private fun getRoles(familyId: Any, vararg accountIds: Any) =
		stub.getMemberRoles(
			GetMemberRolesRequest.newBuilder()
				.setFamilyId(familyId.toString())
				.addAllAccountIds(accountIds.map { it.toString() })
				.build(),
		).rolesMap

	@Test
	fun `여러 계정을 한 번에 확인하면 구성원만 역할과 함께 돌려준다`() {
		val familyId = createFamily()
		val member = familyMemberRepository.save(
			FamilyMember(familyId = familyId, accountId = UUID.randomUUID(), role = FamilyRole.MEMBER),
		)
		val left = familyMemberRepository.save(
			FamilyMember(familyId = familyId, accountId = UUID.randomUUID(), role = FamilyRole.MEMBER),
		)
		left.delete()
		familyMemberRepository.save(left)
		val otherFamilyMember = UUID.randomUUID()
		familyMemberRepository.save(FamilyMember(familyId = createFamily(), accountId = otherFamilyMember, role = FamilyRole.OWNER))

		val roles = getRoles(familyId, ownerId, member.accountId, left.accountId, otherFamilyMember, UUID.randomUUID())

		assertEquals(
			mapOf(
				ownerId.toString() to ProtoFamilyRole.FAMILY_ROLE_OWNER,
				member.accountId.toString() to ProtoFamilyRole.FAMILY_ROLE_MEMBER,
			),
			roles,
		)
		assertEquals(emptyMap(), getRoles(familyId))
	}
}
