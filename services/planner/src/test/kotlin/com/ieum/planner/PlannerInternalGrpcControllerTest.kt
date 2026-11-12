package com.ieum.planner

import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.grpc.client.family.FamilyInfo
import com.ieum.grpc.client.family.FamilyRole
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
import com.ieum.web.exception.IeumException
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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
		scheduleRepository.deleteAllInBatch()
		`when`(familyClient.getFamily(familyId)).thenReturn(
			FamilyInfo(
				"우리집",
				mapOf(mom to FamilyRole.OWNER, dad to FamilyRole.MANAGER, kid to FamilyRole.MEMBER, grandma to FamilyRole.MEMBER),
			),
		)
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
		family: UUID = familyId,
	) = todoRepository.save(
		Todo(
			parentTodoId = null,
			familyId = if (visibility == Visibility.FAMILY) family else null,
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
		visibility: Visibility = Visibility.FAMILY,
	) {
		val schedule = scheduleRepository.save(
			Schedule(
				familyId = if (visibility == Visibility.FAMILY) familyId else null,
				creatorAccountId = mom,
				title = "일정",
				memo = null,
				visibility = visibility,
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
	fun `자정에 끝나는 시각 일정은 오늘 일정으로 세지 않는다`() {
		schedule(today.minusDays(1).atTime(22, 0), today.atStartOfDay(), attendees = listOf(dad))
		schedule(today.atStartOfDay(), today.atStartOfDay(), attendees = listOf(kid))

		assertEquals(mapOf(kid.toString() to counts(schedule = 1)), countsOn("2026-10-30"))
	}

	@Test
	fun `챙길 게 없으면 빈 응답이다`() {
		todo(creator = mom, assignee = dad, dueDate = today.plusDays(1))

		assertEquals(emptyMap(), countsOn("2026-10-30"))
	}

	@Test
	fun `가족을 떠난 사람은 맡은 할 일과 참석 일정을 세지 않는다`() {
		val left = UUID.randomUUID()
		todo(creator = mom, assignee = left, dueDate = today)
		todo(creator = left, assignee = dad, dueDate = today.minusDays(1))
		schedule(today.atTime(10, 0), today.atTime(12, 0), attendees = listOf(left, dad))

		assertEquals(
			mapOf(
				mom.toString() to counts(todo = 1),
				dad.toString() to counts(overdueTodo = 1, schedule = 1),
			),
			countsOn("2026-10-30"),
		)
	}

	@Test
	fun `한 가족을 떠나도 남아 있는 가족의 몫은 센다`() {
		val otherFamilyId = UUID.randomUUID()
		`when`(familyClient.getFamily(familyId)).thenReturn(FamilyInfo("우리집", mapOf(mom to FamilyRole.OWNER)))
		`when`(familyClient.getFamily(otherFamilyId)).thenReturn(FamilyInfo("할머니댁", mapOf(dad to FamilyRole.OWNER)))
		todo(creator = mom, assignee = dad, dueDate = today)
		todo(creator = dad, assignee = dad, dueDate = today, family = otherFamilyId)

		assertEquals(
			mapOf(mom.toString() to counts(todo = 1), dad.toString() to counts(todo = 1)),
			countsOn("2026-10-30"),
		)
	}

	@Test
	fun `개인 일정은 가족 조회 없이 참석자 몫으로 센다`() {
		schedule(today.atTime(10, 0), today.atTime(12, 0), attendees = listOf(kid), visibility = Visibility.PRIVATE)

		assertEquals(mapOf(kid.toString() to counts(schedule = 1)), countsOn("2026-10-30"))
		verify(familyClient, never()).getFamily(familyId)
	}

	@Test
	fun `없는 가족의 항목은 건너뛰고 개인 할 일은 센다`() {
		`when`(familyClient.getFamily(familyId)).thenReturn(null)
		todo(creator = mom, assignee = dad, dueDate = today)
		schedule(today.atTime(10, 0), today.atTime(12, 0), attendees = listOf(dad))
		todo(creator = kid, assignee = kid, dueDate = today, visibility = Visibility.PRIVATE)

		assertEquals(mapOf(kid.toString() to counts(todo = 1)), countsOn("2026-10-30"))
	}

	@Test
	fun `같은 가족 항목이 여러 개여도 가족 조회는 한 번이다`() {
		todo(creator = mom, assignee = dad, dueDate = today)
		todo(creator = dad, assignee = mom, dueDate = today)
		schedule(today.atTime(10, 0), today.atTime(12, 0), attendees = listOf(dad))

		countsOn("2026-10-30")

		verify(familyClient, times(1)).getFamily(familyId)
	}

	@Test
	fun `가족 조회가 실패하면 일부만 세지 않고 실패로 응답한다`() {
		`when`(familyClient.getFamily(familyId)).thenThrow(IeumException(GrpcClientErrorCode.FAMILY_SERVICE_UNAVAILABLE))
		todo(creator = mom, assignee = dad, dueDate = today)
		todo(creator = kid, assignee = kid, dueDate = today, visibility = Visibility.PRIVATE)

		val e = assertFailsWith<StatusRuntimeException> { countsOn("2026-10-30") }

		assertEquals(Status.Code.UNAVAILABLE, e.status.code)
	}

	@Test
	fun `날짜 형식이 틀리면 INVALID_ARGUMENT`() {
		val e = assertFailsWith<StatusRuntimeException> { countsOn("2026/10/30") }

		assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
	}
}
