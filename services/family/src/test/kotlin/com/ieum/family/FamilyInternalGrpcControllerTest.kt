package com.ieum.family

import com.ieum.family.domain.entity.Family
import com.ieum.family.domain.entity.FamilyMember
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.repository.FamilyInvitationRepository
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.family.repository.FamilyRepository
import com.ieum.proto.family.v1.FamilyInternalServiceGrpc
import com.ieum.proto.family.v1.GetFamilyIdsRequest
import com.ieum.proto.family.v1.GetFamilyRequest
import com.ieum.proto.family.v1.GetMemberRoleRequest
import com.ieum.proto.family.v1.GetMemberRolesRequest
import com.ieum.proto.family.v1.GetNicknamesRequest
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
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = ownerId, role = FamilyRole.OWNER, nickname = "구성원"))
		return familyId
	}

	private fun getRole(familyId: String, accountId: String) =
		stub.getMemberRole(
			GetMemberRoleRequest.newBuilder().setFamilyId(familyId).setAccountId(accountId).build(),
		)

	@Test
	fun `닉네임은 나간 구성원의 마지막 닉네임을 담고 구성원이었던 적 없는 계정은 뺀다`() {
		val familyId = createFamily()
		val left = UUID.randomUUID()
		val rejoined = UUID.randomUUID()
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = left, role = FamilyRole.MEMBER, nickname = "삼촌").apply { delete() })
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = rejoined, role = FamilyRole.MEMBER, nickname = "옛 이름").apply { delete() })
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = rejoined, role = FamilyRole.MEMBER, nickname = "새 이름"))
		familyMemberRepository.save(FamilyMember(familyId = createFamily(), accountId = UUID.randomUUID(), role = FamilyRole.MEMBER, nickname = "남"))

		val response = stub.getNicknames(
			GetNicknamesRequest.newBuilder()
				.setFamilyId(familyId.toString())
				.addAllAccountIds(listOf(ownerId, left, rejoined, UUID.randomUUID()).map(UUID::toString))
				.build(),
		)

		assertEquals(
			mapOf(ownerId.toString() to "구성원", left.toString() to "삼촌", rejoined.toString() to "새 이름"),
			response.nicknamesMap,
		)
	}

	private fun assertStatus(code: Status.Code, call: () -> Unit) =
		assertEquals(code, assertFailsWith<StatusRuntimeException> { call() }.status.code)

	@Test
	fun `구성원이면 역할을 반환한다`() {
		val familyId = createFamily()
		val manager = familyMemberRepository.save(
			FamilyMember(familyId = familyId, accountId = UUID.randomUUID(), role = FamilyRole.MANAGER, nickname = "구성원"),
		)

		assertEquals(ProtoFamilyRole.FAMILY_ROLE_OWNER, getRole(familyId.toString(), ownerId.toString()).role)
		assertEquals(ProtoFamilyRole.FAMILY_ROLE_MANAGER, getRole(familyId.toString(), manager.accountId.toString()).role)
	}

	@Test
	fun `구성원이 아니면 NOT_FOUND`() {
		val familyId = createFamily()

		assertStatus(Status.Code.NOT_FOUND) { getRole(familyId.toString(), UUID.randomUUID().toString()) }
	}

	@Test
	fun `내보낸 구성원이면 NOT_FOUND`() {
		val familyId = createFamily()
		val member = familyMemberRepository.save(
			FamilyMember(familyId = familyId, accountId = UUID.randomUUID(), role = FamilyRole.MEMBER, nickname = "구성원"),
		)
		member.delete()
		familyMemberRepository.save(member)

		assertStatus(Status.Code.NOT_FOUND) { getRole(familyId.toString(), member.accountId.toString()) }
	}

	@Test
	fun `UUID 형식이 아니면 INVALID_ARGUMENT`() {
		assertStatus(Status.Code.INVALID_ARGUMENT) { getRole("not-a-uuid", ownerId.toString()) }
		assertStatus(Status.Code.INVALID_ARGUMENT) { getRoles(createFamily().toString(), ownerId.toString(), "not-a-uuid") }
	}

	private fun getRoles(familyId: String, vararg accountIds: String) =
		stub.getMemberRoles(
			GetMemberRolesRequest.newBuilder()
				.setFamilyId(familyId)
				.addAllAccountIds(accountIds.toList())
				.build(),
		).rolesMap

	@Test
	fun `여러 계정을 한 번에 확인하면 구성원만 역할과 함께 돌려준다`() {
		val familyId = createFamily()
		val member = familyMemberRepository.save(
			FamilyMember(familyId = familyId, accountId = UUID.randomUUID(), role = FamilyRole.MEMBER, nickname = "구성원"),
		)
		val left = familyMemberRepository.save(
			FamilyMember(familyId = familyId, accountId = UUID.randomUUID(), role = FamilyRole.MEMBER, nickname = "구성원"),
		)
		left.delete()
		familyMemberRepository.save(left)
		val otherFamilyMember = UUID.randomUUID()
		familyMemberRepository.save(FamilyMember(familyId = createFamily(), accountId = otherFamilyMember, role = FamilyRole.OWNER, nickname = "구성원"))

		val roles = getRoles(familyId.toString(), ownerId.toString(), member.accountId.toString(), left.accountId.toString(), otherFamilyMember.toString(), UUID.randomUUID().toString())

		assertEquals(
			mapOf(
				ownerId.toString() to ProtoFamilyRole.FAMILY_ROLE_OWNER,
				member.accountId.toString() to ProtoFamilyRole.FAMILY_ROLE_MEMBER,
			),
			roles,
		)
		assertEquals(emptyMap(), getRoles(familyId.toString()))
	}

	private fun getFamily(familyId: String) =
		stub.getFamily(GetFamilyRequest.newBuilder().setFamilyId(familyId).build())

	@Test
	fun `가족 이름과 현재 구성원을 돌려준다`() {
		val familyId = createFamily()
		val manager = familyMemberRepository.save(
			FamilyMember(familyId = familyId, accountId = UUID.randomUUID(), role = FamilyRole.MANAGER, nickname = "구성원"),
		)

		val family = getFamily(familyId.toString())

		assertEquals("우리집", family.name)
		assertEquals(
			mapOf(ownerId.toString() to ProtoFamilyRole.FAMILY_ROLE_OWNER, manager.accountId.toString() to ProtoFamilyRole.FAMILY_ROLE_MANAGER),
			family.membersList.associate { it.accountId to it.role },
		)
	}

	@Test
	fun `내보낸 구성원은 가족 정보에서 빠진다`() {
		val familyId = createFamily()
		val left = familyMemberRepository.save(
			FamilyMember(familyId = familyId, accountId = UUID.randomUUID(), role = FamilyRole.MEMBER, nickname = "구성원"),
		)
		left.delete()
		familyMemberRepository.save(left)

		assertEquals(listOf(ownerId.toString()), getFamily(familyId.toString()).membersList.map { it.accountId })
	}

	@Test
	fun `없거나 삭제된 가족은 NOT_FOUND`() {
		val deleted = familyRepository.save(Family(name = "옛집")).apply { delete() }
		familyRepository.save(deleted)

		assertStatus(Status.Code.NOT_FOUND) { getFamily(UUID.randomUUID().toString()) }
		assertStatus(Status.Code.NOT_FOUND) { getFamily(requireNotNull(deleted.familyId).toString()) }
		assertStatus(Status.Code.INVALID_ARGUMENT) { getFamily("not-a-uuid") }
	}

	private fun getFamilyIds(accountId: UUID) =
		stub.getFamilyIds(GetFamilyIdsRequest.newBuilder().setAccountId(accountId.toString()).build()).familyIdsList

	@Test
	fun `계정이 속한 가족을 모두 돌려주고 떠난 가족은 뺀다`() {
		val first = createFamily()
		val second = createFamily()
		val left = createFamily()
		val mine = requireNotNull(familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(left, ownerId))
		mine.delete()
		familyMemberRepository.save(mine)

		assertEquals(listOf(first.toString(), second.toString()), getFamilyIds(ownerId))
	}

	@Test
	fun `속한 가족이 없으면 빈 목록을 돌려준다`() {
		createFamily()

		assertEquals(emptyList(), getFamilyIds(UUID.randomUUID()))
	}
}
