package com.ieum.planner

import com.ieum.events.NotificationEvent
import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.grpc.client.family.FamilyInfo
import com.ieum.grpc.client.family.FamilyRole
import com.ieum.planner.repository.ScheduleAttendeeRepository
import com.ieum.planner.repository.ScheduleRepository
import com.ieum.planner.repository.TodoChecklistItemRepository
import com.ieum.planner.repository.TodoRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class PlannerNotificationEventTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val objectMapper: ObjectMapper,
	private val todoRepository: TodoRepository,
	private val todoChecklistItemRepository: TodoChecklistItemRepository,
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	@Autowired
	private lateinit var events: ApplicationEvents

	private val familyId = UUID.randomUUID()
	private val me = UUID.randomUUID()
	private val member = UUID.randomUUID()
	private val kid = UUID.randomUUID()
	private val newbie = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		todoChecklistItemRepository.deleteAll()
		todoRepository.deleteAllInBatch()
		scheduleAttendeeRepository.deleteAll()
		scheduleRepository.deleteAll()
		listOf(me, member, kid, newbie).forEach { `when`(familyClient.isMember(familyId, it)).thenReturn(true) }
	}

	private fun published() = events.stream(NotificationEvent::class.java).toList()

	private fun send(method: String, uri: String, accountId: UUID = me, body: String? = null) =
		when (method) {
			"POST" -> mockMvc.post(uri) { auth(accountId, body) }
			"PATCH" -> mockMvc.patch(uri) { auth(accountId, body) }
			else -> mockMvc.delete(uri) { auth(accountId, body) }
		}.andReturn().response

	private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.auth(accountId: UUID, body: String?) {
		header("Authorization", "Bearer ${accessToken(accountId)}")
		body?.let {
			contentType = MediaType.APPLICATION_JSON
			content = it
		}
	}

	private fun id(response: org.springframework.mock.web.MockHttpServletResponse, field: String): UUID =
		UUID.fromString(objectMapper.readTree(response.contentAsString).get("data").get(field).asString())

	private fun familyTodo(assignee: UUID, title: String = "분리수거하기") =
		"""{"familyId":"$familyId","visibility":"FAMILY","title":"$title","assigneeAccountId":"$assignee",""" +
			""""dueDate":"2026-11-01","dueTime":"18:00:00"}"""

	private fun todoEvent(type: NotificationEventType, recipient: UUID, todoId: UUID, actor: UUID, title: String = "분리수거하기") =
		NotificationEvent(
			type,
			NotificationEventData(
				recipientAccountIds = listOf(recipient),
				familyId = familyId,
				targetType = NotificationTargetType.TODO,
				targetId = todoId,
				actorAccountId = actor,
				subject = title,
				at = LocalDateTime.of(2026, 11, 1, 18, 0),
			),
		)

	@Test
	fun `가족 할 일을 다른 구성원에게 맡기면 담당자에게 알린다`() {
		val todoId = id(send("POST", "/api/v1/todo", body = familyTodo(member)), "todoId")

		assertEquals(listOf(todoEvent(NotificationEventType.TODO_ASSIGNED, member, todoId, me)), published())
		// 커밋 뒤에만 Kafka 로 옮기는 리스너까지 이어지는지 본다.
		verify(notificationEventRelay).relay(todoEvent(NotificationEventType.TODO_ASSIGNED, member, todoId, me))
	}

	@Test
	fun `내가 담당자인 할 일은 알리지 않는다`() {
		send("POST", "/api/v1/todo", body = familyTodo(me))
		send("POST", "/api/v1/todo", body = """{"visibility":"PRIVATE","title":"운동"}""")

		assertEquals(emptyList<NotificationEvent>(), published())
	}

	@Test
	fun `담당자가 바뀔 때만 새 담당자에게 알린다`() {
		val todoId = id(send("POST", "/api/v1/todo", body = familyTodo(me)), "todoId")
		val update = { assignee: UUID ->
			"""{"title":"분리수거하기","assigneeAccountId":"$assignee","priority":"MEDIUM","dueDate":"2026-11-01","dueTime":"18:00:00"}"""
		}

		send("PATCH", "/api/v1/todo/$todoId", body = update(kid))
		send("PATCH", "/api/v1/todo/$todoId", body = update(kid))

		assertEquals(listOf(todoEvent(NotificationEventType.TODO_ASSIGNED, kid, todoId, me)), published())
	}

	@Test
	fun `맡긴 할 일을 마치면 만든 사람에게 한 번만 알린다`() {
		val todoId = id(send("POST", "/api/v1/todo", body = familyTodo(member)), "todoId")
		send("POST", "/api/v1/todo", body = """{"parentTodoId":"$todoId","title":"봉투 사기","assigneeAccountId":"$member"}""")
		events.clear()

		send("POST", "/api/v1/todo/$todoId/complete", accountId = member)
		send("POST", "/api/v1/todo/$todoId/complete", accountId = member)

		assertEquals(listOf(todoEvent(NotificationEventType.TODO_COMPLETED, me, todoId, member)), published())
	}

	private fun familySchedule(attendees: List<UUID>, isAllFamily: Boolean = false, visibility: String = "FAMILY") =
		"""{"familyId":"$familyId","visibility":"$visibility","title":"할머니 생신","isAllDay":false,""" +
			""""startAt":"2026-11-10T18:00:00","endAt":"2026-11-10T20:00:00","isAllFamily":$isAllFamily,""" +
			""""attendeeAccountIds":[${attendees.joinToString(",") { "\"$it\"" }}]}"""

	private fun scheduleEvent(
		type: NotificationEventType,
		recipients: List<UUID>,
		scheduleId: UUID,
		title: String = "할머니 생신",
		at: LocalDateTime = LocalDateTime.of(2026, 11, 10, 18, 0),
	) = NotificationEvent(
		type,
		NotificationEventData(
			recipientAccountIds = recipients,
			familyId = familyId,
			targetType = NotificationTargetType.SCHEDULE,
			targetId = scheduleId,
			actorAccountId = me,
			subject = title,
			at = at,
		),
	)

	private fun createSchedule(attendees: List<UUID>): UUID {
		`when`(familyClient.getRoles(familyId, attendees.filter { it != me })).thenReturn(
			attendees.filter { it != me }.associateWith { FamilyRole.MEMBER },
		)
		return id(send("POST", "/api/v1/schedule", body = familySchedule(attendees)), "scheduleId")
	}

	@Test
	fun `참석자로 고른 구성원에게 알린다`() {
		val scheduleId = createSchedule(listOf(me, member))

		assertEquals(listOf(scheduleEvent(NotificationEventType.SCHEDULE_ATTENDEE_ADDED, listOf(member), scheduleId)), published())
	}

	@Test
	fun `가족 전체를 고르면 지금 구성원 모두를 참석자로 넣고 가족 일정으로 알린다`() {
		`when`(familyClient.getFamily(familyId)).thenReturn(
			FamilyInfo("우리집", linkedMapOf(me to FamilyRole.OWNER, member to FamilyRole.MEMBER, kid to FamilyRole.MEMBER)),
		)

		val response = send("POST", "/api/v1/schedule", body = familySchedule(emptyList(), isAllFamily = true))
		val scheduleId = id(response, "scheduleId")

		assertEquals(
			listOf(me, member, kid).map { it.toString() },
			objectMapper.readTree(response.contentAsString).get("data").get("attendeeAccountIds").values().map { it.asString() },
		)
		assertEquals(listOf(scheduleEvent(NotificationEventType.FAMILY_SCHEDULE_CREATED, listOf(member, kid), scheduleId)), published())
	}

	@Test
	fun `가족 전체는 참석자 목록이나 개인 일정과 함께 쓸 수 없다`() {
		assertEquals(400, send("POST", "/api/v1/schedule", body = familySchedule(listOf(member), isAllFamily = true)).status)
		assertEquals(
			400,
			send(
				"POST",
				"/api/v1/schedule",
				body = """{"visibility":"PRIVATE","title":"헬스","isAllDay":false,"startAt":"2026-11-10T07:00:00",""" +
					""""endAt":"2026-11-10T08:00:00","isAllFamily":true}""",
			).status,
		)
		assertEquals(emptyList<NotificationEvent>(), published())
	}

	private fun updateSchedule(scheduleId: UUID, attendees: List<UUID>, title: String = "할머니 생신", memo: String? = null, startAt: String = "2026-11-10T18:00:00") =
		send(
			"PATCH",
			"/api/v1/schedule/$scheduleId",
			body = """{"title":"$title","memo":${memo?.let { "\"$it\"" }},"isAllDay":false,"startAt":"$startAt",""" +
				""""endAt":"2026-11-10T21:00:00","attendeeAccountIds":[${attendees.joinToString(",") { "\"$it\"" }}]}""",
		)

	@Test
	fun `일정을 고치면 새 참석자는 추가, 남은 참석자는 변경, 빠진 참석자는 취소 알림을 받는다`() {
		val scheduleId = createSchedule(listOf(me, member, kid))
		`when`(familyClient.getRoles(familyId, listOf(newbie))).thenReturn(mapOf(newbie to FamilyRole.MEMBER))
		events.clear()

		updateSchedule(scheduleId, listOf(me, member, newbie), startAt = "2026-11-10T19:00:00")

		val at = LocalDateTime.of(2026, 11, 10, 19, 0)
		assertEquals(
			listOf(
				scheduleEvent(NotificationEventType.SCHEDULE_ATTENDEE_ADDED, listOf(newbie), scheduleId, at = at),
				scheduleEvent(NotificationEventType.SCHEDULE_UPDATED, listOf(member), scheduleId, at = at),
				scheduleEvent(NotificationEventType.SCHEDULE_CANCELED, listOf(kid), scheduleId, at = at),
			),
			published(),
		)
	}

	@Test
	fun `메모만 고치면 변경 알림을 보내지 않는다`() {
		val scheduleId = createSchedule(listOf(me, member))
		updateSchedule(scheduleId, listOf(me, member))
		events.clear()

		updateSchedule(scheduleId, listOf(me, member), memo = "케이크 챙기기")

		assertEquals(emptyList<NotificationEvent>(), published())
	}

	@Test
	fun `일정을 지우면 참석자에게 취소를 알린다`() {
		val scheduleId = createSchedule(listOf(me, member, kid))
		events.clear()

		send("DELETE", "/api/v1/schedule/$scheduleId")

		assertEquals(listOf(scheduleEvent(NotificationEventType.SCHEDULE_CANCELED, listOf(member, kid), scheduleId)), published())
	}
}
