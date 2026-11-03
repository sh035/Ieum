package com.ieum.planner

import com.ieum.grpc.client.family.FamilyClient
import com.ieum.planner.domain.entity.Schedule
import com.ieum.planner.domain.entity.ScheduleAttendee
import com.ieum.planner.domain.entity.Todo
import com.ieum.planner.domain.enums.TodoPriority
import com.ieum.planner.domain.enums.Visibility
import com.ieum.planner.repository.ScheduleAttendeeRepository
import com.ieum.planner.repository.ScheduleRepository
import com.ieum.planner.repository.TodoChecklistItemRepository
import com.ieum.planner.repository.TodoRepository
import com.ieum.proto.planner.v1.BriefingCounts
import com.ieum.proto.planner.v1.GetBriefingCountsRequest
import com.ieum.proto.planner.v1.PlannerInternalServiceGrpc
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.grpc.client.GrpcChannelFactory
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@AutoConfigureTestGrpcTransport
class PlannerInternalGrpcControllerTest @Autowired constructor(
	channels: GrpcChannelFactory,
	private val todoRepository: TodoRepository,
	private val todoChecklistItemRepository: TodoChecklistItemRepository,
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val stub = PlannerInternalServiceGrpc.newBlockingStub(channels.createChannel("planner"))
	private val familyId = UUID.randomUUID()
	private val mom = UUID.randomUUID()
	private val dad = UUID.randomUUID()
	private val kid = UUID.randomUUID()
	private val grandma = UUID.randomUUID()
	private val today = LocalDate.of(2026, 10, 30)

	@BeforeEach
	fun setUp() {
		todoChecklistItemRepository.deleteAll()
		todoRepository.deleteAllInBatch()
		scheduleAttendeeRepository.deleteAll()
		scheduleRepository.deleteAll()
	}

	private fun countsOn(date: String) =
		stub.getBriefingCounts(GetBriefingCountsRequest.newBuilder().setDate(date).build()).countsMap

	private fun counts(todo: Int = 0, overdueTodo: Int = 0, schedule: Int = 0) =
		BriefingCounts.newBuilder().setTodo(todo).setOverdueTodo(overdueTodo).setSchedule(schedule).build()

	private fun todo(
		creator: UUID,
		assignee: UUID,
		dueDate: LocalDate?,
		completed: Boolean = false,
		deleted: Boolean = false,
		visibility: Visibility = Visibility.FAMILY,
	) = todoRepository.save(
		Todo(
			parentTodoId = null,
			familyId = if (visibility == Visibility.FAMILY) familyId else null,
			creatorAccountId = creator,
			assigneeAccountId = assignee,
			title = "할 일",
			memo = null,
			visibility = visibility,
			priority = TodoPriority.MEDIUM,
			dueDate = dueDate,
			dueTime = null,
		).apply {
			isCompleted = completed
			if (deleted) delete()
		},
	)

	private fun schedule(
		startAt: LocalDateTime,
		endAt: LocalDateTime,
		attendees: List<UUID>,
		isAllDay: Boolean = false,
		deleted: Boolean = false,
		removedAttendees: List<UUID> = emptyList(),
	) {
		val schedule = scheduleRepository.save(
			Schedule(
				familyId = familyId,
				creatorAccountId = mom,
				title = "일정",
				memo = null,
				visibility = Visibility.FAMILY,
				isAllDay = isAllDay,
				startAt = startAt,
				endAt = endAt,
			).apply { if (deleted) delete() },
		)
		val scheduleId = requireNotNull(schedule.scheduleId)
		scheduleAttendeeRepository.saveAll(
			attendees.map { ScheduleAttendee(scheduleId = scheduleId, accountId = it) } +
				removedAttendees.map { ScheduleAttendee(scheduleId = scheduleId, accountId = it).apply { delete() } },
		)
	}

	@Test
	fun `내 할 일과 참석하는 일정을 계정별로 센다`() {
		todo(creator = mom, assignee = dad, dueDate = today)
		todo(creator = kid, assignee = kid, dueDate = today, visibility = Visibility.PRIVATE)
		todo(creator = mom, assignee = dad, dueDate = today.minusDays(3))
		todo(creator = mom, assignee = dad, dueDate = today, completed = true)
		todo(creator = mom, assignee = dad, dueDate = today, deleted = true)
		todo(creator = mom, assignee = dad, dueDate = today.plusDays(1))
		todo(creator = mom, assignee = grandma, dueDate = null)

		schedule(today.atTime(10, 0), today.atTime(12, 0), attendees = listOf(mom, dad), removedAttendees = listOf(grandma))
		schedule(today.atStartOfDay(), today.atStartOfDay(), attendees = listOf(kid), isAllDay = true)
		schedule(today.minusDays(1).atTime(20, 0), today.atTime(1, 0), attendees = listOf(mom))
		schedule(today.plusDays(1).atStartOfDay(), today.plusDays(1).atTime(1, 0), attendees = listOf(dad))
		schedule(today.atTime(15, 0), today.atTime(16, 0), attendees = listOf(kid), deleted = true)

		assertEquals(
			mapOf(
				mom.toString() to counts(todo = 1, overdueTodo = 1, schedule = 2),
				dad.toString() to counts(todo = 1, overdueTodo = 1, schedule = 1),
				kid.toString() to counts(todo = 1, schedule = 1),
			),
			countsOn("2026-10-30"),
		)
	}

	@Test
	fun `챙길 게 없으면 빈 응답이다`() {
		todo(creator = mom, assignee = dad, dueDate = today.plusDays(1))

		assertEquals(emptyMap(), countsOn("2026-10-30"))
	}

	@Test
	fun `날짜 형식이 틀리면 INVALID_ARGUMENT`() {
		val e = assertFailsWith<StatusRuntimeException> { countsOn("2026/10/30") }

		assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
	}
}
