package com.ieum.planner

import com.ieum.events.FamilyEventData
import com.ieum.events.IeumEvent
import com.ieum.events.Topics
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
import com.ieum.planner.service.PlannerFamilyEventReceiver
import com.ieum.web.config.TimeZoneConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
class PlannerFamilyEventTest @Autowired constructor(
	private val familyEventReceiver: PlannerFamilyEventReceiver,
	private val todoRepository: TodoRepository,
	private val todoChecklistItemRepository: TodoChecklistItemRepository,
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
	private val objectMapper: ObjectMapper,
	private val registry: KafkaListenerEndpointRegistry,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val home = UUID.randomUUID()
	private val otherHome = UUID.randomUUID()
	private val leaver = UUID.randomUUID()
	private val stayer = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		todoChecklistItemRepository.deleteAllInBatch()
		scheduleAttendeeRepository.deleteAllInBatch()
		scheduleRepository.deleteAllInBatch()
		// 하위 할 일이 부모를 가리키므로 한 문장으로 지운다.
		todoRepository.deleteAllInBatch()
	}

	private fun todo(familyId: UUID?, creator: UUID, assignee: UUID = creator, parentTodoId: UUID? = null) = requireNotNull(
		todoRepository.save(
			Todo(
				parentTodoId = parentTodoId,
				familyId = familyId,
				creatorAccountId = creator,
				assigneeAccountId = assignee,
				title = "장보기",
				memo = null,
				visibility = if (familyId == null) Visibility.PRIVATE else Visibility.FAMILY,
				priority = TodoPriority.NONE,
				dueDate = null,
				dueTime = null,
			),
		).todoId,
	)

	private fun schedule(familyId: UUID?, creator: UUID) = requireNotNull(
		scheduleRepository.save(
			Schedule(
				familyId = familyId,
				creatorAccountId = creator,
				title = "외식",
				memo = null,
				visibility = if (familyId == null) Visibility.PRIVATE else Visibility.FAMILY,
				isAllDay = false,
				startAt = LocalDateTime.of(2026, 11, 10, 18, 0),
				endAt = LocalDateTime.of(2026, 11, 10, 20, 0),
			),
		).scheduleId,
	)

	private fun attend(scheduleId: UUID, accountId: UUID) =
		requireNotNull(scheduleAttendeeRepository.save(ScheduleAttendee(scheduleId = scheduleId, accountId = accountId)).scheduleAttendeeId)

	// family 는 이벤트 시각을 서울 시계로 찍는다.
	private fun seoulNow() = LocalDateTime.now(Clock.system(TimeZoneConfig.SEOUL))

	private fun send(type: String, data: FamilyEventData, createdAt: LocalDateTime = seoulNow().plusMinutes(1)) =
		familyEventReceiver.receive(objectMapper.writeValueAsString(IeumEvent(UUID.randomUUID(), type, createdAt, data)))

	private fun todoDeleted(todoId: UUID) = todoRepository.findById(todoId).get().isDeleted

	private fun scheduleDeleted(scheduleId: UUID) = scheduleRepository.findById(scheduleId).get().isDeleted

	private fun attendeeDeleted(attendeeId: UUID) = scheduleAttendeeRepository.findById(attendeeId).get().isDeleted

	@Test
	fun `가족 이벤트 토픽을 구독한다`() {
		assertEquals(listOf(listOf(Topics.FAMILY)), registry.listenerContainers.map { it.containerProperties.topics?.toList() })
	}

	@Test
	fun `가족을 떠난 사람이 그 가족에 남긴 할 일과 일정, 참석을 지운다`() {
		val leaversTodo = todo(home, leaver)
		val subtaskOfLeaversTodo = todo(home, stayer, parentTodoId = leaversTodo)
		val assignedToLeaver = todo(home, stayer, assignee = leaver)
		val stayersTodo = todo(home, stayer)
		val leaversOtherHomeTodo = todo(otherHome, leaver)
		val leaversPrivateTodo = todo(null, leaver)

		val leaversSchedule = schedule(home, leaver)
		val stayersSchedule = schedule(home, stayer)
		val leaverAttending = attend(stayersSchedule, leaver)
		val stayerAttending = attend(stayersSchedule, stayer)
		val leaversOtherHomeSchedule = schedule(otherHome, leaver)

		send("FAMILY_MEMBER_LEFT", FamilyEventData(familyId = home, accountId = leaver))

		assertEquals(
			listOf(true, true, true, false, false, false),
			listOf(leaversTodo, subtaskOfLeaversTodo, assignedToLeaver, stayersTodo, leaversOtherHomeTodo, leaversPrivateTodo).map { todoDeleted(it) },
		)
		assertEquals(listOf(true, false, false), listOf(leaversSchedule, stayersSchedule, leaversOtherHomeSchedule).map { scheduleDeleted(it) })
		assertEquals(listOf(true, false), listOf(leaverAttending, stayerAttending).map { attendeeDeleted(it) })
	}

	@Test
	fun `같은 이벤트가 두 번 와도 결과가 같다`() {
		val leaversTodo = todo(home, leaver)
		val stayersTodo = todo(home, stayer)

		repeat(2) { send("FAMILY_MEMBER_LEFT", FamilyEventData(familyId = home, accountId = leaver)) }

		assertEquals(listOf(true, false), listOf(leaversTodo, stayersTodo).map { todoDeleted(it) })
	}

	@Test
	fun `떠난 뒤에 다시 들어와 만든 할 일과 일정은 늦게 온 이벤트로 지우지 않는다`() {
		val afterRejoinTodo = todo(home, leaver)
		val afterRejoinSchedule = schedule(home, leaver)
		val afterRejoinAttending = attend(schedule(home, stayer), leaver)

		send("FAMILY_MEMBER_LEFT", FamilyEventData(familyId = home, accountId = leaver), createdAt = seoulNow().minusMinutes(1))

		assertEquals(
			listOf(false, false, false),
			listOf(todoDeleted(afterRejoinTodo), scheduleDeleted(afterRejoinSchedule), attendeeDeleted(afterRejoinAttending)),
		)
	}

	@Test
	fun `가족이 삭제되면 그 가족의 할 일과 일정을 지운다`() {
		val homeTodo = todo(home, stayer)
		val otherHomeTodo = todo(otherHome, stayer)
		val homeSchedule = schedule(home, stayer)
		val otherHomeSchedule = schedule(otherHome, stayer)

		send("FAMILY_DELETED", FamilyEventData(familyId = home))

		assertEquals(listOf(true, false), listOf(homeTodo, otherHomeTodo).map { todoDeleted(it) })
		assertEquals(listOf(true, false), listOf(homeSchedule, otherHomeSchedule).map { scheduleDeleted(it) })
	}
}
