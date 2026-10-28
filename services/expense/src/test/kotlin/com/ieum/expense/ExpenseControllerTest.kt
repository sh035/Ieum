package com.ieum.expense

import com.ieum.expense.repository.ExpenseRepository
import com.ieum.expense.service.FamilyClient
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
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ExpenseControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val expenseRepository: ExpenseRepository,
	private val objectMapper: ObjectMapper,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val familyId = UUID.randomUUID()
	private val me = UUID.randomUUID()
	private val member = UUID.randomUUID()
	private val outsider = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		expenseRepository.deleteAllInBatch()
		listOf(me, member).forEach { `when`(familyClient.isMember(familyId, it)).thenReturn(true) }
	}

	private fun expense(
		visibility: String = "FAMILY",
		title: String = "이마트 장보기",
		amount: Int = 45200,
		payer: UUID? = null,
		extra: String = "",
	): String {
		val payerField = payer?.let { ""","payerAccountId":"$it"""" } ?: ""
		return """{"familyId":"$familyId","visibility":"$visibility","title":"$title","amount":$amount,""" +
			""""category":"MART","spentDate":"2026-10-28"$payerField$extra}"""
	}

	private fun create(body: String, accountId: UUID = me) = mockMvc.post("/api/v1/expense") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun createId(body: String, accountId: UUID = me): String {
		val response = create(body, accountId).andReturn().response.contentAsString
		return objectMapper.readTree(response).get("data").get("expenseId").asString()
	}

	private fun get(expenseId: String, accountId: UUID = me) = mockMvc.get("/api/v1/expense/$expenseId") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	private fun update(expenseId: String, payer: UUID, accountId: UUID = me, amount: Int = 50000) =
		mockMvc.patch("/api/v1/expense/$expenseId") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"코스트코","amount":$amount,"category":"FOOD","spentDate":"2026-10-27","payerAccountId":"$payer"}"""
		}

	@Test
	fun `가족 지출을 만들면 결제한 사람은 기본으로 본인이다`() {
		create(expense(extra = ""","memo":"한 달 장보기"""")).andExpect {
			status { isCreated() }
			jsonPath("\$.data.expenseId") { exists() }
			jsonPath("\$.data.familyId") { value(familyId.toString()) }
			jsonPath("\$.data.creatorAccountId") { value(me.toString()) }
			jsonPath("\$.data.payerAccountId") { value(me.toString()) }
			jsonPath("\$.data.amount") { value(45200) }
			jsonPath("\$.data.memo") { value("한 달 장보기") }
			jsonPath("\$.data.spentDate") { value("2026-10-28") }
		}
	}

	@Test
	fun `가족 지출은 구성원을 결제한 사람으로 지정할 수 있다`() {
		create(expense(payer = member)).andExpect {
			status { isCreated() }
			jsonPath("\$.data.payerAccountId") { value(member.toString()) }
		}
	}

	@Test
	fun `가족 밖 사람이나 개인 지출의 다른 결제자는 400`() {
		create(expense(payer = outsider)).andExpect { status { isBadRequest() } }
		create(expense(visibility = "PRIVATE", payer = member)).andExpect { status { isBadRequest() } }
	}

	@Test
	fun `구성원이 아니면 404`() {
		create(expense(), outsider).andExpect { status { isNotFound() } }
	}

	@Test
	fun `잘못된 입력은 400`() {
		create(expense(amount = 0)).andExpect { status { isBadRequest() } }
		create(expense(title = " ")).andExpect { status { isBadRequest() } }
		create(expense(extra = ""","memo":"${"가".repeat(2001)}"""")).andExpect { status { isBadRequest() } }
		create(expense().replace("MART", "TOY")).andExpect { status { isBadRequest() } }
	}

	@Test
	fun `가족 지출은 구성원 누구나 보고 개인 지출은 만든 사람만 본다`() {
		val familyExpenseId = createId(expense())
		val privateExpenseId = createId(expense(visibility = "PRIVATE"))

		get(familyExpenseId, member).andExpect { status { isOk() } }
		get(familyExpenseId, outsider).andExpect { status { isNotFound() } }
		get(privateExpenseId, member).andExpect { status { isNotFound() } }
		get(privateExpenseId).andExpect {
			status { isOk() }
			jsonPath("\$.data.visibility") { value("PRIVATE") }
		}
	}

	@Test
	fun `수정은 보낸 값으로 바꾸고 결제한 사람을 바꿀 때만 구성원을 확인한다`() {
		val expenseId = createId(expense(payer = member, extra = ""","memo":"메모""""))
		`when`(familyClient.isMember(familyId, member)).thenReturn(false)

		update(expenseId, payer = member).andExpect {
			status { isOk() }
			jsonPath("\$.data.title") { value("코스트코") }
			jsonPath("\$.data.amount") { value(50000) }
			jsonPath("\$.data.category") { value("FOOD") }
			jsonPath("\$.data.spentDate") { value("2026-10-27") }
			jsonPath("\$.data.memo") { value(null) }
		}
		update(expenseId, payer = outsider).andExpect { status { isBadRequest() } }
		update(expenseId, payer = me, amount = -1).andExpect { status { isBadRequest() } }
	}

	@Test
	fun `삭제하면 조회되지 않는다`() {
		val expenseId = createId(expense())

		mockMvc.delete("/api/v1/expense/$expenseId") { header("Authorization", "Bearer ${accessToken(me)}") }
			.andExpect { status { isNoContent() } }
		get(expenseId).andExpect { status { isNotFound() } }
		mockMvc.delete("/api/v1/expense/$expenseId") { header("Authorization", "Bearer ${accessToken(me)}") }
			.andExpect { status { isNotFound() } }
	}
}
