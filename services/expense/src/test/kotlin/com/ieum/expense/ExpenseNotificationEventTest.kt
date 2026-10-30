package com.ieum.expense

import com.ieum.events.NotificationEvent
import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.expense.domain.entity.Budget
import com.ieum.expense.repository.BudgetRepository
import com.ieum.expense.repository.ExpenseRepository
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.grpc.client.family.FamilyInfo
import com.ieum.grpc.client.family.FamilyRole
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class ExpenseNotificationEventTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val objectMapper: ObjectMapper,
	private val expenseRepository: ExpenseRepository,
	private val budgetRepository: BudgetRepository,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	@Autowired
	private lateinit var events: ApplicationEvents

	private val familyId = UUID.randomUUID()
	private val owner = UUID.randomUUID()
	private val me = UUID.randomUUID()
	private val member = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		expenseRepository.deleteAllInBatch()
		budgetRepository.deleteAllInBatch()
		listOf(owner, me, member).forEach { `when`(familyClient.isMember(familyId, it)).thenReturn(true) }
		`when`(familyClient.getFamily(familyId)).thenReturn(
			FamilyInfo("우리집", linkedMapOf(owner to FamilyRole.OWNER, me to FamilyRole.MANAGER, member to FamilyRole.MEMBER)),
		)
	}

	private fun published() = events.stream(NotificationEvent::class.java).toList()

	private fun create(amount: Int, payer: UUID = me, visibility: String = "FAMILY", accountId: UUID = me): UUID {
		val response = mockMvc.post("/api/v1/expense") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"familyId":"$familyId","visibility":"$visibility","title":"이마트 장보기","amount":$amount,""" +
				""""category":"MART","spentDate":"2026-11-05","payerAccountId":"$payer"}"""
		}.andReturn().response.contentAsString
		return UUID.fromString(objectMapper.readTree(response).get("data").get("expenseId").asString())
	}

	private fun update(expenseId: UUID, amount: Int, payer: UUID) =
		mockMvc.patch("/api/v1/expense/$expenseId") {
			header("Authorization", "Bearer ${accessToken(me)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"이마트 장보기","amount":$amount,"category":"MART","spentDate":"2026-11-05","payerAccountId":"$payer"}"""
		}

	private fun payerEvent(recipient: UUID, expenseId: UUID, amount: Int) = NotificationEvent(
		NotificationEventType.EXPENSE_PAYER_ASSIGNED,
		NotificationEventData(
			recipientAccountIds = listOf(recipient),
			familyId = familyId,
			targetType = NotificationTargetType.EXPENSE,
			targetId = expenseId,
			actorAccountId = me,
			subject = "이마트 장보기",
			amount = amount,
		),
	)

	private fun budgetEvent(recipients: List<UUID>, over: Int) = NotificationEvent(
		NotificationEventType.BUDGET_EXCEEDED,
		NotificationEventData(
			recipientAccountIds = recipients,
			familyId = familyId,
			targetType = NotificationTargetType.BUDGET,
			actorAccountId = me,
			amount = over,
			dedupKey = "BUDGET_EXCEEDED:$familyId:FAMILY:2026-11",
		),
	)

	@Test
	fun `다른 구성원을 결제자로 등록하면 결제자에게 알린다`() {
		val expenseId = create(12000, payer = member)
		create(8000, payer = me)

		assertEquals(listOf(payerEvent(member, expenseId, 12000)), published())
	}

	@Test
	fun `결제자가 바뀔 때만 새 결제자에게 알린다`() {
		val expenseId = create(12000)

		update(expenseId, 12000, payer = owner)
		update(expenseId, 15000, payer = owner)

		assertEquals(listOf(payerEvent(owner, expenseId, 12000)), published())
	}

	@Test
	fun `가족 예산을 넘는 순간 넘긴 본인을 뺀 OWNER 와 MANAGER 에게 초과액을 알린다`() {
		budgetRepository.save(Budget(familyId = familyId, accountId = null, budgetMonth = null, amount = 50000))
		create(30000)
		create(30000)
		create(5000)

		assertEquals(listOf(budgetEvent(listOf(owner), 10000)), published())
	}

	@Test
	fun `지출을 고쳐 예산을 넘겨도 알린다`() {
		budgetRepository.save(Budget(familyId = familyId, accountId = null, budgetMonth = null, amount = 50000))
		val expenseId = create(40000)

		update(expenseId, 52000, payer = me)

		assertEquals(listOf(budgetEvent(listOf(owner), 2000)), published())
	}

	@Test
	fun `개인 지출은 예산 알림을 보내지 않는다`() {
		budgetRepository.save(Budget(familyId = familyId, accountId = null, budgetMonth = null, amount = 50000))
		budgetRepository.save(Budget(familyId = familyId, accountId = me, budgetMonth = null, amount = 10000))

		create(60000, visibility = "PRIVATE")

		assertEquals(emptyList<NotificationEvent>(), published())
	}
}
