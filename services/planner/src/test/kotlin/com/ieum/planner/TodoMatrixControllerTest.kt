package com.ieum.planner

import com.ieum.planner.repository.TodoChecklistItemRepository
import com.ieum.planner.repository.TodoRepository
import com.ieum.planner.service.FamilyClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class TodoMatrixControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val todoRepository: TodoRepository,
	private val todoChecklistItemRepository: TodoChecklistItemRepository,
	private val objectMapper: ObjectMapper,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val familyId = UUID.randomUUID()
	private val me = UUID.randomUUID()
	private val member = UUID.randomUUID()
	private val today = LocalDate.now()

	@BeforeEach
	fun setUp() {
		todoChecklistItemRepository.deleteAll()
		todoRepository.deleteAllInBatch()
		`when`(familyClient.isMember(familyId, me)).thenReturn(true)
		`when`(familyClient.isMember(familyId, member)).thenReturn(true)
	}

	private fun create(title: String, priority: String, dueDate: LocalDate?, assignee: UUID = me, family: Boolean = true): String {
		val due = dueDate?.let { ""","dueDate":"$it"""" } ?: ""
		val body = if (family) {
			"""{"familyId":"$familyId","visibility":"FAMILY","title":"$title","priority":"$priority","assigneeAccountId":"$assignee"$due}"""
		} else {
			"""{"visibility":"PRIVATE","title":"$title","priority":"$priority"$due}"""
		}
		val response = mockMvc.post("/api/v1/todo") {
			header("Authorization", "Bearer ${accessToken(me)}")
			contentType = MediaType.APPLICATION_JSON
			content = body
		}.andReturn().response.contentAsString

		return objectMapper.readTree(response).get("data").get("todoId").asString()
	}

	private fun matrix(query: String = "?familyId=$familyId", accountId: UUID = me) = mockMvc.get("/api/v1/todo/matrix$query") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	@Test
	fun `우선순위와 마감일로 4칸에 나눈다`() {
		create("지난 할 일", "HIGH", today.minusDays(3))
		create("내일 마감", "MEDIUM", today.plusDays(1))
		create("모레 마감", "HIGH", today.plusDays(2))
		create("마감 없음 중요", "HIGH", null)
		create("오늘 마감 사소", "LOW", today)
		create("마감 없음 사소", "NONE", null, family = false)

		matrix().andExpect {
			status { isOk() }
			jsonPath("\$.data.importantUrgent.length()") { value(2) }
			jsonPath("\$.data.importantUrgent[0].title") { value("지난 할 일") }
			jsonPath("\$.data.importantUrgent[1].title") { value("내일 마감") }
			jsonPath("\$.data.importantNotUrgent.length()") { value(2) }
			jsonPath("\$.data.importantNotUrgent[0].title") { value("모레 마감") }
			jsonPath("\$.data.importantNotUrgent[1].title") { value("마감 없음 중요") }
			jsonPath("\$.data.notImportantUrgent.length()") { value(1) }
			jsonPath("\$.data.notImportantUrgent[0].title") { value("오늘 마감 사소") }
			jsonPath("\$.data.notImportantNotUrgent.length()") { value(1) }
			jsonPath("\$.data.notImportantNotUrgent[0].title") { value("마감 없음 사소") }
		}
	}

	@Test
	fun `남이 담당자인 할 일과 완료한 할 일은 제외한다`() {
		create("남의 일", "HIGH", today, assignee = member)
		val doneId = create("끝난 일", "HIGH", today)
		mockMvc.post("/api/v1/todo/$doneId/complete") { header("Authorization", "Bearer ${accessToken(me)}") }

		matrix().andExpect { jsonPath("\$.data.importantUrgent") { isEmpty() } }
	}

	@Test
	fun `가족을 지정하지 않으면 개인 할 일만 나온다`() {
		create("가족 일", "HIGH", today)
		create("개인 일", "HIGH", today, family = false)

		matrix(query = "").andExpect {
			jsonPath("\$.data.importantUrgent.length()") { value(1) }
			jsonPath("\$.data.importantUrgent[0].title") { value("개인 일") }
		}
	}

	@Test
	fun `가족 구성원이 아니면 404`() {
		matrix(accountId = UUID.randomUUID()).andExpect { status { isNotFound() } }
	}
}
