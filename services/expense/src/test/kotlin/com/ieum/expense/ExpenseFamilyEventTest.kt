package com.ieum.expense

import com.ieum.events.FamilyEventData
import com.ieum.events.IeumEvent
import com.ieum.events.Topics
import com.ieum.expense.domain.entity.Budget
import com.ieum.expense.domain.entity.Expense
import com.ieum.expense.domain.enums.ExpenseCategory
import com.ieum.expense.domain.enums.Visibility
import com.ieum.expense.repository.BudgetRepository
import com.ieum.expense.repository.ExpenseRepository
import com.ieum.expense.service.ExpenseFamilyEventReceiver
import com.ieum.grpc.client.family.FamilyClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
class ExpenseFamilyEventTest @Autowired constructor(
	private val familyEventReceiver: ExpenseFamilyEventReceiver,
	private val expenseRepository: ExpenseRepository,
	private val budgetRepository: BudgetRepository,
	private val objectMapper: ObjectMapper,
	private val registry: KafkaListenerEndpointRegistry,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val home = UUID.randomUUID()
	private val otherHome = UUID.randomUUID()
	private val leaver = UUID.randomUUID()
	private val stayer = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		expenseRepository.deleteAllInBatch()
		budgetRepository.deleteAllInBatch()
	}

	private fun expense(familyId: UUID, creator: UUID, visibility: Visibility) = requireNotNull(
		expenseRepository.save(
			Expense(
				familyId = familyId,
				creatorAccountId = creator,
				payerAccountId = creator,
				title = "장보기",
				memo = null,
				amount = 12000,
				category = ExpenseCategory.MART,
				visibility = visibility,
				spentDate = LocalDate.of(2026, 11, 4),
			),
		).expenseId,
	)

	private fun budget(familyId: UUID, accountId: UUID?, budgetMonth: LocalDate? = null) = requireNotNull(
		budgetRepository.save(Budget(familyId = familyId, accountId = accountId, budgetMonth = budgetMonth, amount = 500000)).budgetId,
	)

	private fun send(type: String, data: FamilyEventData) =
		familyEventReceiver.receive(objectMapper.writeValueAsString(IeumEvent(UUID.randomUUID(), type, LocalDateTime.now(), data)))

	private fun expenseDeleted(expenseId: UUID) = expenseRepository.findById(expenseId).get().isDeleted

	private fun budgetDeleted(budgetId: UUID) = budgetRepository.findById(budgetId).get().isDeleted

	@Test
	fun `가족 이벤트 토픽을 구독한다`() {
		assertEquals(listOf(listOf(Topics.FAMILY)), registry.listenerContainers.map { it.containerProperties.topics?.toList() })
	}

	@Test
	fun `가족이 삭제되면 그 가족의 지출과 예산을 지운다`() {
		val familyExpense = expense(home, stayer, Visibility.FAMILY)
		val privateExpense = expense(home, stayer, Visibility.PRIVATE)
		val otherHomeExpense = expense(otherHome, stayer, Visibility.FAMILY)
		val familyBudget = budget(home, null)
		val personalBudget = budget(home, stayer)
		val monthlyBudget = budget(home, null, LocalDate.of(2026, 11, 1))
		val otherHomeBudget = budget(otherHome, null)

		send("FAMILY_DELETED", FamilyEventData(familyId = home))

		assertEquals(listOf(true, true, false), listOf(familyExpense, privateExpense, otherHomeExpense).map { expenseDeleted(it) })
		// 한 문장으로 지워도 수정 시각을 남긴다.
		assertEquals(listOf(true, true, false), listOf(familyExpense, privateExpense, otherHomeExpense).map { expenseRepository.findById(it).get().updatedAt != null })
		assertEquals(listOf(true, true, true, false), listOf(familyBudget, personalBudget, monthlyBudget, otherHomeBudget).map { budgetDeleted(it) })
	}

	@Test
	fun `구성원이 떠나도 지출과 예산은 가족 기록으로 남긴다`() {
		val leaversExpense = expense(home, leaver, Visibility.FAMILY)
		val leaversPrivateExpense = expense(home, leaver, Visibility.PRIVATE)
		val leaversBudget = budget(home, leaver)

		send("FAMILY_MEMBER_LEFT", FamilyEventData(familyId = home, accountId = leaver))

		assertEquals(
			listOf(false, false, false),
			listOf(expenseDeleted(leaversExpense), expenseDeleted(leaversPrivateExpense), budgetDeleted(leaversBudget)),
		)
	}
}
