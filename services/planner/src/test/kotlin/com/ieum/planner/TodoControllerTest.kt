package com.ieum.planner

import com.ieum.planner.repository.TodoChecklistItemRepository
import com.ieum.planner.repository.TodoRepository
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
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class TodoControllerTest @Autowired constructor(
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
	private val outsider = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		todoChecklistItemRepository.deleteAll()
		todoRepository.deleteAllInBatch()
		`when`(familyClient.isMember(familyId, me)).thenReturn(true)
		`when`(familyClient.isMember(familyId, member)).thenReturn(true)
	}

	private fun create(body: String, accountId: UUID = me) = mockMvc.post("/api/v1/todo") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun createId(body: String, accountId: UUID = me): String {
		val response = create(body, accountId).andReturn().response.contentAsString
		return objectMapper.readTree(response).get("data").get("todoId").asString()
	}

	private fun familyTodo(title: String = "분리수거", assignee: UUID = me) =
		"""{"familyId":"$familyId","visibility":"FAMILY","title":"$title","assigneeAccountId":"$assignee"}"""

	private fun privateTodo(title: String = "운동") = """{"visibility":"PRIVATE","title":"$title"}"""

	private fun get(uri: String, accountId: UUID = me) = mockMvc.get(uri) {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	@Test
	fun `가족 할 일을 만들면 전체를 반환한다`() {
		create(familyTodo(assignee = member)).andExpect {
			status { isCreated() }
			jsonPath("\$.data.todoId") { exists() }
			jsonPath("\$.data.familyId") { value(familyId.toString()) }
			jsonPath("\$.data.creatorAccountId") { value(me.toString()) }
			jsonPath("\$.data.assigneeAccountId") { value(member.toString()) }
			jsonPath("\$.data.priority") { value("NONE") }
			jsonPath("\$.data.isCompleted") { value(false) }
			jsonPath("\$.data.checklistItems") { isEmpty() }
		}
	}

	@Test
	fun `개인 할 일은 가족 없이 만들고 담당자는 본인이다`() {
		create(privateTodo()).andExpect {
			status { isCreated() }
			jsonPath("\$.data.familyId") { doesNotExist() }
			jsonPath("\$.data.assigneeAccountId") { value(me.toString()) }
		}
	}

	@Test
	fun `가족 구성원이 아니면 404`() {
		create(familyTodo(), accountId = outsider).andExpect { status { isNotFound() } }
	}

	@Test
	fun `가족 할 일에 담당자가 없으면 400`() {
		create("""{"familyId":"$familyId","visibility":"FAMILY","title":"분리수거"}""").andExpect { status { isBadRequest() } }
		create("""{"familyId":"$familyId","visibility":"FAMILY","title":"분리수거","assigneeAccountId":""}""")
			.andExpect { status { isBadRequest() } }
	}

	@Test
	fun `가족 할 일을 수정하며 담당자를 비우면 400`() {
		val todoId = createId(familyTodo(assignee = member))

		mockMvc.patch("/api/v1/todo/$todoId") {
			header("Authorization", "Bearer ${accessToken(me)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"분리수거","priority":"NONE"}"""
		}.andExpect { status { isBadRequest() } }
	}

	@Test
	fun `가족이 아닌 사람을 담당자로 지정하면 400`() {
		create(familyTodo(assignee = outsider)).andExpect { status { isBadRequest() } }
	}

	@Test
	fun `가족과 공개 범위가 맞지 않으면 400`() {
		create("""{"visibility":"FAMILY","title":"분리수거","assigneeAccountId":"$me"}""").andExpect { status { isBadRequest() } }
		create("""{"familyId":"$familyId","visibility":"PRIVATE","title":"운동"}""").andExpect { status { isBadRequest() } }
		create("""{"visibility":"PRIVATE","title":"운동","assigneeAccountId":"$member"}""").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `마감일 없이 마감 시간만 주면 400`() {
		create("""{"visibility":"PRIVATE","title":"운동","dueTime":"09:00:00"}""").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `목록은 가족 할 일과 내 개인 할 일만 반환한다`() {
		createId(familyTodo("분리수거"))
		createId(privateTodo("내 운동"))
		createId(privateTodo("남의 운동"), accountId = member)

		get("/api/v1/todo?familyId=$familyId").andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(2) }
			jsonPath("\$.data[0].title") { value("분리수거") }
			jsonPath("\$.data[1].title") { value("내 운동") }
		}
		get("/api/v1/todo").andExpect { jsonPath("\$.data.length()") { value(1) } }
	}

	@Test
	fun `남의 개인 할 일과 다른 가족의 할 일은 404`() {
		val privateId = createId(privateTodo(), accountId = member)
		val familyTodoId = createId(familyTodo())

		get("/api/v1/todo/$privateId").andExpect { status { isNotFound() } }
		get("/api/v1/todo/$familyTodoId", accountId = outsider).andExpect { status { isNotFound() } }
	}

	@Test
	fun `수정하면 null로 보낸 값은 비워진다`() {
		val todoId = createId("""{"visibility":"PRIVATE","title":"운동","memo":"메모","dueDate":"2026-11-01"}""")

		mockMvc.patch("/api/v1/todo/$todoId") {
			header("Authorization", "Bearer ${accessToken(me)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"헬스","priority":"HIGH","memo":null,"dueDate":null}"""
		}.andExpect {
			status { isOk() }
			jsonPath("\$.data.title") { value("헬스") }
			jsonPath("\$.data.priority") { value("HIGH") }
			jsonPath("\$.data.memo") { doesNotExist() }
			jsonPath("\$.data.dueDate") { doesNotExist() }
		}
	}

	@Test
	fun `담당자가 가족을 나가도 담당자를 그대로 두면 수정할 수 있다`() {
		val todoId = createId(familyTodo(assignee = member))
		`when`(familyClient.isMember(familyId, member)).thenReturn(false)

		mockMvc.patch("/api/v1/todo/$todoId") {
			header("Authorization", "Bearer ${accessToken(me)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"분리수거 2","priority":"NONE","assigneeAccountId":"$member"}"""
		}.andExpect {
			status { isOk() }
			jsonPath("\$.data.title") { value("분리수거 2") }
		}
	}

	@Test
	fun `메모가 2000자를 넘으면 400`() {
		create("""{"visibility":"PRIVATE","title":"운동","memo":"${"a".repeat(2001)}"}""").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `완료와 완료 취소`() {
		val todoId = createId(familyTodo())
		val auth = "Bearer ${accessToken(member)}"

		mockMvc.post("/api/v1/todo/$todoId/complete") { header("Authorization", auth) }
			.andExpect { status { isNoContent() } }
		get("/api/v1/todo/$todoId").andExpect { jsonPath("\$.data.isCompleted") { value(true) } }

		mockMvc.delete("/api/v1/todo/$todoId/complete") { header("Authorization", auth) }
			.andExpect { status { isNoContent() } }
		get("/api/v1/todo/$todoId").andExpect { jsonPath("\$.data.isCompleted") { value(false) } }
	}

	@Test
	fun `삭제하면 조회되지 않는다`() {
		val todoId = createId(privateTodo())

		mockMvc.delete("/api/v1/todo/$todoId") { header("Authorization", "Bearer ${accessToken(me)}") }
			.andExpect { status { isNoContent() } }
		get("/api/v1/todo/$todoId").andExpect { status { isNotFound() } }
	}
}
