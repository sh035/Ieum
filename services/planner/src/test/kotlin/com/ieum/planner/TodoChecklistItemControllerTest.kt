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
class TodoChecklistItemControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val todoRepository: TodoRepository,
	private val todoChecklistItemRepository: TodoChecklistItemRepository,
	private val objectMapper: ObjectMapper,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val familyId = UUID.randomUUID()
	private val me = UUID.randomUUID()
	private val outsider = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		todoChecklistItemRepository.deleteAll()
		todoRepository.deleteAllInBatch()
		`when`(familyClient.isMember(familyId, me)).thenReturn(true)
	}

	private fun createTodo(body: String = """{"familyId":"$familyId","visibility":"FAMILY","title":"장보기","assigneeAccountId":"$me"}"""): String {
		val response = mockMvc.post("/api/v1/todo") {
			header("Authorization", "Bearer ${accessToken(me)}")
			contentType = MediaType.APPLICATION_JSON
			content = body
		}.andReturn().response.contentAsString

		return objectMapper.readTree(response).get("data").get("todoId").asString()
	}

	private fun addItem(todoId: String, content: String, accountId: UUID = me) =
		mockMvc.post("/api/v1/todo/$todoId/checklist-item") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			contentType = MediaType.APPLICATION_JSON
			this.content = """{"content":"$content"}"""
		}

	private fun addItemId(todoId: String, content: String): String {
		val response = addItem(todoId, content).andReturn().response.contentAsString
		return objectMapper.readTree(response).get("data").get("todoChecklistItemId").asString()
	}

	private fun getTodo(todoId: String) = mockMvc.get("/api/v1/todo/$todoId") {
		header("Authorization", "Bearer ${accessToken(me)}")
	}

	@Test
	fun `항목을 추가하면 할 일 조회와 목록에 생성 순서대로 포함된다`() {
		val todoId = createTodo()

		addItem(todoId, "우유").andExpect {
			status { isCreated() }
			jsonPath("\$.data.todoChecklistItemId") { exists() }
			jsonPath("\$.data.content") { value("우유") }
			jsonPath("\$.data.isCompleted") { value(false) }
		}
		addItem(todoId, "계란")

		getTodo(todoId).andExpect {
			jsonPath("\$.data.checklistItems.length()") { value(2) }
			jsonPath("\$.data.checklistItems[0].content") { value("우유") }
			jsonPath("\$.data.checklistItems[1].content") { value("계란") }
		}
		mockMvc.get("/api/v1/todo?familyId=$familyId") {
			header("Authorization", "Bearer ${accessToken(me)}")
		}.andExpect { jsonPath("\$.data[0].checklistItems.length()") { value(2) } }
	}

	@Test
	fun `내용 수정과 완료, 완료 취소`() {
		val todoId = createTodo()
		val itemId = addItemId(todoId, "우유")
		val uri = "/api/v1/todo/$todoId/checklist-item/$itemId"
		val auth = "Bearer ${accessToken(me)}"

		mockMvc.patch(uri) {
			header("Authorization", auth)
			contentType = MediaType.APPLICATION_JSON
			content = """{"content":"저지방 우유"}"""
		}.andExpect {
			status { isOk() }
			jsonPath("\$.data.content") { value("저지방 우유") }
		}

		mockMvc.post("$uri/complete") { header("Authorization", auth) }.andExpect { status { isNoContent() } }
		getTodo(todoId).andExpect { jsonPath("\$.data.checklistItems[0].isCompleted") { value(true) } }

		mockMvc.delete("$uri/complete") { header("Authorization", auth) }.andExpect { status { isNoContent() } }
		getTodo(todoId).andExpect { jsonPath("\$.data.checklistItems[0].isCompleted") { value(false) } }
	}

	@Test
	fun `삭제하면 할 일 조회에서 빠진다`() {
		val todoId = createTodo()
		val itemId = addItemId(todoId, "우유")

		mockMvc.delete("/api/v1/todo/$todoId/checklist-item/$itemId") {
			header("Authorization", "Bearer ${accessToken(me)}")
		}.andExpect { status { isNoContent() } }

		getTodo(todoId).andExpect { jsonPath("\$.data.checklistItems") { isEmpty() } }
	}

	@Test
	fun `할 일을 볼 수 없으면 404`() {
		val todoId = createTodo()

		addItem(todoId, "우유", accountId = outsider).andExpect { status { isNotFound() } }
	}

	@Test
	fun `다른 할 일의 항목 ID로 접근하면 404`() {
		val itemId = addItemId(createTodo(), "우유")
		val otherTodoId = createTodo()

		mockMvc.post("/api/v1/todo/$otherTodoId/checklist-item/$itemId/complete") {
			header("Authorization", "Bearer ${accessToken(me)}")
		}.andExpect { status { isNotFound() } }
	}

	@Test
	fun `내용이 비었거나 255자를 넘으면 400`() {
		val todoId = createTodo()

		addItem(todoId, " ").andExpect { status { isBadRequest() } }
		addItem(todoId, "a".repeat(256)).andExpect { status { isBadRequest() } }
	}
}
