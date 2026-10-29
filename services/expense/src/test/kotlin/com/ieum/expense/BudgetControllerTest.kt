package com.ieum.expense

import com.ieum.expense.domain.entity.Expense
import com.ieum.expense.domain.enums.ExpenseCategory
import com.ieum.grpc.client.family.FamilyRole
import com.ieum.expense.domain.enums.Visibility
import com.ieum.expense.repository.BudgetRepository
import com.ieum.expense.repository.ExpenseRepository
import com.ieum.grpc.client.family.FamilyClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class BudgetControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val expenseRepository: ExpenseRepository,
	private val budgetRepository: BudgetRepository,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val familyId = UUID.randomUUID()
	private val owner = UUID.randomUUID()
	private val manager = UUID.randomUUID()
	private val member = UUID.randomUUID()
	private val outsider = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		expenseRepository.deleteAllInBatch()
		budgetRepository.deleteAllInBatch()
		mapOf(owner to FamilyRole.OWNER, manager to FamilyRole.MANAGER, member to FamilyRole.MEMBER).forEach { (id, role) ->
			`when`(familyClient.getRole(familyId, id)).thenReturn(role)
			`when`(familyClient.isMember(familyId, id)).thenReturn(true)
		}
	}

	private fun setBudget(scope: String, amount: Int, month: String? = null, accountId: UUID = owner) =
		mockMvc.patch("/api/v1/expense/budget") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			contentType = MediaType.APPLICATION_JSON
			val monthField = month?.let { ""","month":"$it"""" } ?: ""
			content = """{"familyId":"$familyId","scope":"$scope","amount":$amount$monthField}"""
		}

	private fun deleteBudget(scope: String, month: String? = null, accountId: UUID = owner) =
		mockMvc.delete("/api/v1/expense/budget") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			param("familyId", familyId.toString())
			param("scope", scope)
			month?.let { param("month", it) }
		}

	private fun monthly(month: String = "2026-10", accountId: UUID = owner) = mockMvc.get("/api/v1/expense") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
		param("familyId", familyId.toString())
		param("month", month)
	}

	private fun spend(
		amount: Int,
		date: String,
		category: ExpenseCategory = ExpenseCategory.FOOD,
		visibility: Visibility = Visibility.FAMILY,
		creator: UUID = owner,
		title: String = "지출",
	) = expenseRepository.save(
		Expense(
			familyId = familyId,
			creatorAccountId = creator,
			payerAccountId = creator,
			title = title,
			memo = null,
			amount = amount,
			category = category,
			visibility = visibility,
			spentDate = LocalDate.parse(date),
		),
	)

	@Test
	fun `가족 예산은 OWNER와 MANAGER만 정하고 MEMBER는 403`() {
		setBudget("FAMILY", 1_500_000).andExpect {
			status { isOk() }
			jsonPath("\$.data.scope") { value("FAMILY") }
			jsonPath("\$.data.month") { value(null) }
			jsonPath("\$.data.amount") { value(1_500_000) }
		}
		setBudget("FAMILY", 2_000_000, month = "2026-09", accountId = manager).andExpect {
			status { isOk() }
			jsonPath("\$.data.month") { value("2026-09") }
		}
		setBudget("FAMILY", 100, accountId = member).andExpect { status { isForbidden() } }
	}

	@Test
	fun `개인 예산은 누구나 자기 것만 정하고 비구성원은 404`() {
		setBudget("PERSONAL", 300_000, accountId = member).andExpect { status { isOk() } }
		setBudget("PERSONAL", 300_000, accountId = outsider).andExpect { status { isNotFound() } }
	}

	@Test
	fun `같은 예산을 다시 정하면 금액만 바뀐다`() {
		setBudget("FAMILY", 1_000_000)
		setBudget("FAMILY", 1_200_000)

		monthly().andExpect { jsonPath("\$.data.family.budget.amount") { value(1_200_000) } }
		assertEquals(1L, budgetRepository.count())
	}

	@Test
	fun `음수 예산이나 잘못된 달은 400`() {
		setBudget("FAMILY", -1).andExpect { status { isBadRequest() } }
		setBudget("FAMILY", 100, month = "2026-13").andExpect { status { isBadRequest() } }
		monthly(month = "2026-13").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `월별 조회는 가족과 개인을 따로 집계하고 그 달 예산이 기본 예산보다 우선한다`() {
		setBudget("FAMILY", 1_000_000)
		setBudget("FAMILY", 100_000, month = "2026-10")
		setBudget("PERSONAL", 50_000)
		spend(60_000, "2026-10-03", ExpenseCategory.FOOD, title = "외식")
		spend(70_000, "2026-10-20", ExpenseCategory.MART, title = "장보기")
		spend(9_000, "2026-10-10", ExpenseCategory.CAFE_SNACK, Visibility.PRIVATE, title = "내 커피")
		spend(5_000, "2026-10-11", ExpenseCategory.CAFE_SNACK, Visibility.PRIVATE, creator = member, title = "남의 커피")
		spend(1_000, "2026-09-30", title = "지난달")
		spend(1_000, "2026-11-01", title = "다음달")

		monthly().andExpect {
			status { isOk() }
			jsonPath("\$.data.month") { value("2026-10") }
			jsonPath("\$.data.family.total") { value(130_000) }
			jsonPath("\$.data.family.categoryTotals.FOOD") { value(60_000) }
			jsonPath("\$.data.family.categoryTotals.MART") { value(70_000) }
			jsonPath("\$.data.family.categoryTotals.ETC") { value(0) }
			jsonPath("\$.data.family.budget.amount") { value(100_000) }
			jsonPath("\$.data.family.budget.remaining") { value(-30_000) }
			jsonPath("\$.data.family.expenses.length()") { value(2) }
			jsonPath("\$.data.family.expenses[0].title") { value("장보기") }
			jsonPath("\$.data.family.expenses[1].title") { value("외식") }
			jsonPath("\$.data.personal.total") { value(9_000) }
			jsonPath("\$.data.personal.budget.amount") { value(50_000) }
			jsonPath("\$.data.personal.budget.remaining") { value(41_000) }
			jsonPath("\$.data.personal.expenses.length()") { value(1) }
			jsonPath("\$.data.personal.expenses[0].title") { value("내 커피") }
		}
	}

	@Test
	fun `MEMBER는 가족 합계는 보지만 가족 예산은 받지 못한다`() {
		setBudget("FAMILY", 1_000_000)
		setBudget("PERSONAL", 30_000, accountId = member)
		spend(60_000, "2026-10-03")

		monthly(accountId = member).andExpect {
			status { isOk() }
			jsonPath("\$.data.family.total") { value(60_000) }
			jsonPath("\$.data.family.budget") { value(null) }
			jsonPath("\$.data.personal.budget.amount") { value(30_000) }
		}
		monthly(accountId = manager).andExpect { jsonPath("\$.data.family.budget.amount") { value(1_000_000) } }
	}

	@Test
	fun `그 달 예산을 지우면 기본 예산으로 돌아가고 없는 예산은 404`() {
		setBudget("FAMILY", 1_000_000)
		setBudget("FAMILY", 100_000, month = "2026-10")

		deleteBudget("FAMILY", month = "2026-10").andExpect { status { isNoContent() } }
		monthly().andExpect { jsonPath("\$.data.family.budget.amount") { value(1_000_000) } }
		deleteBudget("FAMILY", month = "2026-10").andExpect { status { isNotFound() } }
		deleteBudget("FAMILY", accountId = member).andExpect { status { isForbidden() } }
	}

	@Test
	fun `예산이 없으면 budget 은 null 이고 비구성원 조회는 404`() {
		monthly().andExpect {
			status { isOk() }
			jsonPath("\$.data.family.budget") { value(null) }
			jsonPath("\$.data.personal.budget") { value(null) }
			jsonPath("\$.data.family.total") { value(0) }
		}
		monthly(accountId = outsider).andExpect { status { isNotFound() } }
	}
}
