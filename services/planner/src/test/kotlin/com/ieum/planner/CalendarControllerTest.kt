package com.ieum.planner

import com.ieum.planner.repository.ScheduleAttendeeRepository
import com.ieum.planner.repository.ScheduleRepository
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
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class CalendarControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val todoRepository: TodoRepository,
	private val todoChecklistItemRepository: TodoChecklistItemRepository,
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
	private val objectMapper: ObjectMapper,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val familyId = UUID.randomUUID()
	private val me = UUID.randomUUID()
	private val other = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		todoChecklistItemRepository.deleteAll()
		todoRepository.deleteAllInBatch()
		scheduleAttendeeRepository.deleteAll()
		scheduleRepository.deleteAll()
		`when`(familyClient.isMember(familyId, me)).thenReturn(true)
	}

	private fun post(uri: String, body: String, accountId: UUID = me): String =
		mockMvc.post(uri) {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			contentType = MediaType.APPLICATION_JSON
			content = body
		}.andReturn().response.contentAsString

	private fun familyTodo(title: String, dueDate: String?): String {
		val due = dueDate?.let { ""","dueDate":"$it"""" } ?: ""
		val response = post(
			"/api/v1/todo",
			"""{"familyId":"$familyId","visibility":"FAMILY","title":"$title","priority":"NONE","assigneeAccountId":"$me"$due}""",
		)
		return objectMapper.readTree(response).get("data").get("todoId").asString()
	}

	private fun privateTodo(title: String, dueDate: String, dueTime: String? = null, accountId: UUID = me) {
		val time = dueTime?.let { ""","dueTime":"$it"""" } ?: ""
		post("/api/v1/todo", """{"visibility":"PRIVATE","title":"$title","priority":"NONE","dueDate":"$dueDate"$time}""", accountId)
	}

	private fun familySchedule(title: String, startAt: String, endAt: String) = post(
		"/api/v1/schedule",
		"""{"familyId":"$familyId","visibility":"FAMILY","title":"$title","isAllDay":false,"startAt":"$startAt","endAt":"$endAt",""" +
			""""attendeeAccountIds":["$me"]}""",
	)

	private fun privateSchedule(title: String, startAt: String, endAt: String) = post(
		"/api/v1/schedule",
		"""{"visibility":"PRIVATE","title":"$title","isAllDay":false,"startAt":"$startAt","endAt":"$endAt"}""",
	)

	private fun calendar(query: String, accountId: UUID = me) = mockMvc.get("/api/v1/calendar$query") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	@Test
	fun `기간 안의 가족과 개인 할 일, 일정을 합쳐서 반환한다`() {
		val doneId = familyTodo("완료한 가족 할 일", "2026-11-10")
		post("/api/v1/todo/$doneId/complete", "")
		privateTodo("시간 있는 개인 할 일", "2026-11-05", "09:00")
		privateTodo("하루 종일 개인 할 일", "2026-11-05")
		familyTodo("마감 없음", null)
		familyTodo("기간 밖", "2026-12-01")
		privateTodo("남의 개인 할 일", "2026-11-05", accountId = other)

		familySchedule("경계에 걸친 여행", "2026-10-31T10:00:00", "2026-11-01T10:00:00")
		privateSchedule("개인 일정", "2026-11-20T07:00:00", "2026-11-20T08:00:00")
		familySchedule("기간 밖 일정", "2026-12-01T10:00:00", "2026-12-01T11:00:00")

		calendar("?familyId=$familyId&from=2026-11-01&to=2026-11-30").andExpect {
			status { isOk() }
			jsonPath("\$.data.todos.length()") { value(3) }
			jsonPath("\$.data.todos[0].title") { value("하루 종일 개인 할 일") }
			jsonPath("\$.data.todos[1].title") { value("시간 있는 개인 할 일") }
			jsonPath("\$.data.todos[1].visibility") { value("PRIVATE") }
			jsonPath("\$.data.todos[2].title") { value("완료한 가족 할 일") }
			jsonPath("\$.data.todos[2].visibility") { value("FAMILY") }
			jsonPath("\$.data.todos[2].isCompleted") { value(true) }
			jsonPath("\$.data.schedules.length()") { value(2) }
			jsonPath("\$.data.schedules[0].title") { value("경계에 걸친 여행") }
			jsonPath("\$.data.schedules[1].title") { value("개인 일정") }
		}
	}

	@Test
	fun `가족을 지정하지 않으면 개인 항목만 반환한다`() {
		familyTodo("가족 할 일", "2026-11-10")
		privateTodo("개인 할 일", "2026-11-10")
		familySchedule("가족 일정", "2026-11-10T10:00:00", "2026-11-10T11:00:00")

		calendar("?from=2026-11-01&to=2026-11-30").andExpect {
			status { isOk() }
			jsonPath("\$.data.todos.length()") { value(1) }
			jsonPath("\$.data.todos[0].title") { value("개인 할 일") }
			jsonPath("\$.data.schedules.length()") { value(0) }
		}
	}

	@Test
	fun `42일까지 조회할 수 있다`() {
		calendar("?from=2026-11-01&to=2026-12-12").andExpect { status { isOk() } }
	}

	@Test
	fun `42일을 넘으면 400`() {
		calendar("?from=2026-11-01&to=2026-12-13").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `끝이 시작보다 앞서면 400`() {
		calendar("?from=2026-11-30&to=2026-11-01").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `기간을 빼면 400`() {
		calendar("?from=2026-11-01").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `구성원이 아닌 가족이면 404`() {
		calendar("?familyId=$familyId&from=2026-11-01&to=2026-11-30", accountId = other).andExpect { status { isNotFound() } }
	}
}
