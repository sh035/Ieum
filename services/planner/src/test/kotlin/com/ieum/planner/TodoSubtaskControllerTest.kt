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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class TodoSubtaskControllerTest @Autowired constructor(
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

	private fun familyTodo() =
		createId("""{"familyId":"$familyId","visibility":"FAMILY","title":"대청소","assigneeAccountId":"$me"}""")

	private fun subtask(parentId: String, title: String = "하위", assignee: UUID = me) =
		"""{"parentTodoId":"$parentId","title":"$title","assigneeAccountId":"$assignee"}"""

	private fun get(uri: String, accountId: UUID = me) = mockMvc.get(uri) {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	private fun command(uri: String, delete: Boolean = false) {
		if (delete) {
			mockMvc.delete(uri) { header("Authorization", "Bearer ${accessToken(me)}") }
		} else {
			mockMvc.post(uri) { header("Authorization", "Bearer ${accessToken(me)}") }
		}.andExpect { status { isNoContent() } }
	}

	@Test
	fun `하위 할 일은 부모의 공개 범위와 가족을 물려받고 담당자는 따로 지정한다`() {
		val parentId = familyTodo()

		create(subtask(parentId, assignee = member)).andExpect {
			status { isCreated() }
			jsonPath("\$.data.parentTodoId") { value(parentId) }
			jsonPath("\$.data.visibility") { value("FAMILY") }
			jsonPath("\$.data.familyId") { value(familyId.toString()) }
			jsonPath("\$.data.assigneeAccountId") { value(member.toString()) }
		}
	}

	@Test
	fun `손자까지 만들 수 있고 그 아래는 400`() {
		val childId = createId(subtask(familyTodo()))
		val grandchildId = createId(subtask(childId))

		create(subtask(grandchildId)).andExpect { status { isBadRequest() } }
	}

	@Test
	fun `하위 할 일에 공개 범위나 가족을 보내면 400`() {
		val parentId = familyTodo()

		create("""{"parentTodoId":"$parentId","visibility":"FAMILY","title":"하위","assigneeAccountId":"$me"}""")
			.andExpect { status { isBadRequest() } }
		create("""{"parentTodoId":"$parentId","familyId":"$familyId","title":"하위","assigneeAccountId":"$me"}""")
			.andExpect { status { isBadRequest() } }
	}

	@Test
	fun `최상위 할 일에 공개 범위가 없으면 400`() {
		create("""{"title":"운동"}""").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `볼 수 없는 부모에는 하위 할 일을 만들 수 없다`() {
		val familyParentId = familyTodo()
		val privateParentId = createId("""{"visibility":"PRIVATE","title":"개인"}""")

		create(subtask(familyParentId, assignee = outsider), outsider).andExpect { status { isNotFound() } }
		create(subtask(privateParentId, assignee = member), member).andExpect { status { isNotFound() } }
		create(subtask(UUID.randomUUID().toString())).andExpect { status { isNotFound() } }
	}

	@Test
	fun `개인 할 일의 하위 할 일 담당자는 본인뿐이다`() {
		val privateParentId = createId("""{"visibility":"PRIVATE","title":"개인"}""")

		create(subtask(privateParentId, assignee = member)).andExpect { status { isBadRequest() } }
	}

	@Test
	fun `부모를 완료하면 하위도 완료되고 완료 취소는 부모만 되돌린다`() {
		val parentId = familyTodo()
		val childId = createId(subtask(parentId))
		val grandchildId = createId(subtask(childId))

		command("/api/v1/todo/$parentId/complete")
		listOf(parentId, childId, grandchildId).forEach { id ->
			get("/api/v1/todo/$id").andExpect { jsonPath("\$.data.isCompleted") { value(true) } }
		}

		command("/api/v1/todo/$parentId/complete", delete = true)
		get("/api/v1/todo/$parentId").andExpect { jsonPath("\$.data.isCompleted") { value(false) } }
		get("/api/v1/todo/$childId").andExpect { jsonPath("\$.data.isCompleted") { value(true) } }
	}

	@Test
	fun `부모를 삭제하면 하위도 삭제된다`() {
		val parentId = familyTodo()
		val childId = createId(subtask(parentId))
		val grandchildId = createId(subtask(childId))

		command("/api/v1/todo/$parentId", delete = true)

		listOf(parentId, childId, grandchildId).forEach { id ->
			get("/api/v1/todo/$id").andExpect { status { isNotFound() } }
		}
	}

	@Test
	fun `목록에는 부모와 하위가 parentTodoId와 함께 나온다`() {
		val parentId = familyTodo()
		val childId = createId(subtask(parentId))

		get("/api/v1/todo?familyId=$familyId").andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(2) }
			jsonPath("\$.data[0].todoId") { value(parentId) }
			jsonPath("\$.data[0].parentTodoId") { value(null) }
			jsonPath("\$.data[1].todoId") { value(childId) }
			jsonPath("\$.data[1].parentTodoId") { value(parentId) }
		}
	}
}
