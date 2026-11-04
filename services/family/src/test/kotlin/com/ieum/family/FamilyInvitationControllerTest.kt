package com.ieum.family

import com.ieum.family.domain.entity.Family
import com.ieum.family.domain.entity.FamilyInvitation
import com.ieum.family.domain.entity.FamilyMember
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.repository.FamilyInvitationRepository
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.family.repository.FamilyRepository
import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.grpc.client.account.AccountProfile
import com.ieum.web.exception.IeumException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class FamilyInvitationControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val familyInvitationRepository: FamilyInvitationRepository,
	private val objectMapper: ObjectMapper,
) : PostgresTestSupport() {

	@MockitoSpyBean
	private lateinit var familyMemberRepositorySpy: FamilyMemberRepository

	@MockitoSpyBean
	private lateinit var familyRepositorySpy: FamilyRepository

	private val ownerId = UUID.randomUUID()
	private val memberId = UUID.randomUUID()
	private val inviteeId = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		familyInvitationRepository.deleteAll()
		familyMemberRepository.deleteAll()
		familyRepository.deleteAll()
		`when`(accountClient.getNicknames(setOf(inviteeId))).thenReturn(mapOf(inviteeId to "막내"))
		`when`(accountClient.getNicknames(setOf(ownerId))).thenReturn(mapOf(ownerId to "엄마"))
	}

	private fun createFamily(name: String = "우리집"): UUID {
		val familyId = requireNotNull(familyRepository.save(Family(name = name)).familyId)
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = ownerId, role = FamilyRole.OWNER))
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = memberId, role = FamilyRole.MEMBER))
		return familyId
	}

	private fun findCandidate(familyId: UUID, email: String, accountId: UUID = ownerId) =
		mockMvc.get("/api/v1/family/$familyId/invitation/candidate") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			param("email", email)
		}

	private fun invite(familyId: UUID, invitee: UUID = inviteeId, accountId: UUID = ownerId) =
		mockMvc.post("/api/v1/family/$familyId/invitation") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"inviteeAccountId":"$invitee"}"""
		}

	private fun inviteId(familyId: UUID): String {
		val response = invite(familyId).andReturn().response.contentAsString
		return objectMapper.readTree(response).get("data").get("familyInvitationId").asString()
	}

	private fun getSent(familyId: UUID) = mockMvc.get("/api/v1/family/$familyId/invitation") {
		header("Authorization", "Bearer ${accessToken(ownerId)}")
	}

	private fun getReceived(accountId: UUID = inviteeId) = mockMvc.get("/api/v1/family-invitation") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	private fun accept(invitationId: Any, accountId: UUID = inviteeId) = mockMvc.post("/api/v1/family-invitation/$invitationId/accept") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	private fun decline(invitationId: Any, accountId: UUID = inviteeId) = mockMvc.post("/api/v1/family-invitation/$invitationId/decline") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	private fun cancel(familyId: UUID, invitationId: Any, accountId: UUID = ownerId) =
		mockMvc.delete("/api/v1/family/$familyId/invitation/$invitationId") { header("Authorization", "Bearer ${accessToken(accountId)}") }

	private fun expiredInvitation(familyId: UUID) = requireNotNull(
		familyInvitationRepository.save(
			FamilyInvitation(
				familyId = familyId,
				inviterAccountId = ownerId,
				inviteeAccountId = inviteeId,
				expiresAt = LocalDateTime.now().minusMinutes(1),
			),
		).familyInvitationId,
	)

	@Test
	fun `OWNER는 이메일로 초대할 사람을 찾는다`() {
		val familyId = createFamily()
		`when`(accountClient.findByEmail("kid@ieum.com")).thenReturn(AccountProfile(inviteeId, "막내"))

		findCandidate(familyId, " kid@ieum.com ").andExpect {
			status { isOk() }
			jsonPath("\$.data.accountId") { value(inviteeId.toString()) }
			jsonPath("\$.data.nickname") { value("막내") }
		}
	}

	@Test
	fun `없는 이메일이거나 이미 구성원이면 찾을 수 없다`() {
		val familyId = createFamily()
		`when`(accountClient.findByEmail("member@ieum.com")).thenReturn(AccountProfile(memberId, "아빠"))

		findCandidate(familyId, "nobody@ieum.com").andExpect { status { isNotFound() } }
		findCandidate(familyId, "member@ieum.com").andExpect { status { isNotFound() } }
	}

	@Test
	fun `OWNER가 아니면 찾거나 초대할 수 없다`() {
		val familyId = createFamily()

		findCandidate(familyId, "kid@ieum.com", accountId = memberId).andExpect { status { isForbidden() } }
		invite(familyId, accountId = memberId).andExpect { status { isForbidden() } }
		findCandidate(familyId, "kid@ieum.com", accountId = UUID.randomUUID()).andExpect { status { isNotFound() } }
		invite(familyId, accountId = UUID.randomUUID()).andExpect { status { isNotFound() } }
	}

	@Test
	fun `OWNER가 초대하면 받는 사람과 1시간 뒤 만료 시각을 반환한다`() {
		val familyId = createFamily()

		invite(familyId).andExpect {
			status { isCreated() }
			jsonPath("\$.data.familyInvitationId") { exists() }
			jsonPath("\$.data.inviteeAccountId") { value(inviteeId.toString()) }
			jsonPath("\$.data.inviteeNickname") { value("막내") }
			jsonPath("\$.data.expiresAt") { exists() }
		}
	}

	@Test
	fun `살아 있는 초대가 있으면 409이고 만료된 초대는 새로 만들 수 있다`() {
		val familyId = createFamily()
		invite(familyId)

		invite(familyId).andExpect { status { isConflict() } }

		familyInvitationRepository.deleteAll()
		expiredInvitation(familyId)
		invite(familyId).andExpect { status { isCreated() } }
	}

	@Test
	fun `이미 구성원이면 초대할 수 없고 없는 계정은 404`() {
		val familyId = createFamily()
		`when`(accountClient.getNicknames(setOf(memberId))).thenReturn(mapOf(memberId to "아빠"))

		invite(familyId, invitee = memberId).andExpect { status { isConflict() } }
		invite(familyId, invitee = UUID.randomUUID()).andExpect { status { isNotFound() } }
	}

	@Test
	fun `OWNER는 보낸 초대를 보고 취소한다`() {
		val familyId = createFamily()
		val invitationId = inviteId(familyId)
		familyInvitationRepository.save(
			FamilyInvitation(familyId = familyId, inviterAccountId = ownerId, inviteeAccountId = memberId, expiresAt = LocalDateTime.now().minusMinutes(1)),
		)

		getSent(familyId).andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(1) }
			jsonPath("\$.data[0].familyInvitationId") { value(invitationId) }
			jsonPath("\$.data[0].inviteeNickname") { value("막내") }
		}
		cancel(familyId, invitationId).andExpect { status { isNoContent() } }

		getSent(familyId).andExpect { jsonPath("\$.data.length()") { value(0) } }
		accept(invitationId).andExpect { status { isNotFound() } }
	}

	@Test
	fun `받은 초대에는 가족 이름과 초대한 사람이 나오고 만료된 초대는 빠진다`() {
		val familyId = createFamily()
		val invitationId = inviteId(familyId)
		expiredInvitation(createFamily("옆집"))

		getReceived().andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(1) }
			jsonPath("\$.data[0].familyInvitationId") { value(invitationId) }
			jsonPath("\$.data[0].familyId") { value(familyId.toString()) }
			jsonPath("\$.data[0].familyName") { value("우리집") }
			jsonPath("\$.data[0].inviterAccountId") { value(ownerId.toString()) }
			jsonPath("\$.data[0].inviterNickname") { value("엄마") }
		}
	}

	@Test
	fun `수락하면 MEMBER가 되고 초대는 끝난다`() {
		val familyId = createFamily()
		val invitationId = inviteId(familyId)

		accept(invitationId).andExpect {
			status { isCreated() }
			jsonPath("\$.data.familyId") { value(familyId.toString()) }
			jsonPath("\$.data.name") { value("우리집") }
			jsonPath("\$.data.role") { value("MEMBER") }
		}

		assertEquals(FamilyRole.MEMBER, familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, inviteeId)?.role)
		accept(invitationId).andExpect { status { isNotFound() } }
		getReceived().andExpect { jsonPath("\$.data.length()") { value(0) } }
	}

	@Test
	fun `남의 초대나 만료된 초대는 수락하거나 거절할 수 없다`() {
		val familyId = createFamily()
		val invitationId = inviteId(familyId)
		val expiredId = expiredInvitation(createFamily("옆집"))

		accept(invitationId, accountId = memberId).andExpect { status { isNotFound() } }
		decline(invitationId, accountId = memberId).andExpect { status { isNotFound() } }
		accept(expiredId).andExpect { status { isNotFound() } }
		accept(UUID.randomUUID()).andExpect { status { isNotFound() } }
	}

	@Test
	fun `동시 수락으로 exists 확인을 통과해도 409`() {
		val familyId = createFamily()
		val invitationId = inviteId(familyId)
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = inviteeId, role = FamilyRole.MEMBER))
		doReturn(false).`when`(familyMemberRepositorySpy).existsByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, inviteeId)

		accept(invitationId).andExpect { status { isConflict() } }
	}

	@Test
	fun `거절하면 초대가 끝난다`() {
		val familyId = createFamily()
		val invitationId = inviteId(familyId)

		decline(invitationId).andExpect { status { isNoContent() } }

		getReceived().andExpect { jsonPath("\$.data.length()") { value(0) } }
		accept(invitationId).andExpect { status { isNotFound() } }
		invite(familyId).andExpect { status { isCreated() } }
	}

	@Test
	fun `다른 가족의 초대는 취소할 수 없다`() {
		val familyId = createFamily()
		val neighborOwner = UUID.randomUUID()
		val neighborFamilyId = requireNotNull(familyRepository.save(Family(name = "옆집")).familyId)
		familyMemberRepository.save(FamilyMember(familyId = neighborFamilyId, accountId = neighborOwner, role = FamilyRole.OWNER))
		val neighborInvitationId = familyInvitationRepository.save(
			FamilyInvitation(familyId = neighborFamilyId, inviterAccountId = neighborOwner, inviteeAccountId = inviteeId, expiresAt = LocalDateTime.now().plusHours(1)),
		).familyInvitationId

		cancel(familyId, requireNotNull(neighborInvitationId)).andExpect { status { isNotFound() } }

		assertEquals(1, familyInvitationRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(neighborFamilyId).size)
	}

	@Test
	fun `수락 도중 OWNER가 취소하면 가입되지 않는다`() {
		val familyId = createFamily()
		val invitationId = inviteId(familyId)
		val executor = Executors.newSingleThreadExecutor()
		val hooked = AtomicBoolean(false)
		// 수락이 초대를 읽은 뒤 가족을 잠그기 직전에 취소가 먼저 끝나게 한다.
		// Spring Data 리포지토리 spy 는 원래 빈에 위임하는 기본 응답으로 만들어져서 그 응답으로 원래 동작을 부른다.
		val delegateToReal = mockingDetails(familyRepositorySpy).mockCreationSettings.defaultAnswer
		doAnswer {
			if (hooked.compareAndSet(false, true)) {
				executor.submit { cancel(familyId, invitationId).andExpect { status { isNoContent() } } }.get()
			}
			delegateToReal.answer(it)
		}.`when`(familyRepositorySpy).findWithLockByFamilyIdAndIsDeletedFalse(familyId)

		accept(invitationId).andExpect { status { isNotFound() } }
		executor.shutdown()

		assertEquals(null, familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, inviteeId))
	}

	@Test
	fun `잘못된 요청은 400`() {
		val familyId = createFamily()

		mockMvc.post("/api/v1/family/$familyId/invitation") {
			header("Authorization", "Bearer ${accessToken(ownerId)}")
			contentType = MediaType.APPLICATION_JSON
			content = "{}"
		}.andExpect { status { isBadRequest() } }
		mockMvc.post("/api/v1/family/$familyId/invitation") {
			header("Authorization", "Bearer ${accessToken(ownerId)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"inviteeAccountId":"not-a-uuid"}"""
		}.andExpect { status { isBadRequest() } }
		mockMvc.get("/api/v1/family/$familyId/invitation/candidate") { header("Authorization", "Bearer ${accessToken(ownerId)}") }
			.andExpect { status { isBadRequest() } }
	}

	@Test
	fun `account 가 응답하지 않으면 503`() {
		val familyId = createFamily()
		`when`(accountClient.findByEmail("kid@ieum.com")).thenThrow(IeumException(GrpcClientErrorCode.ACCOUNT_SERVICE_UNAVAILABLE))

		findCandidate(familyId, "kid@ieum.com").andExpect { status { isServiceUnavailable() } }
	}
}
