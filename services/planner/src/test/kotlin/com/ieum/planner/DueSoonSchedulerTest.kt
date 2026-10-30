package com.ieum.planner

import com.ieum.events.NotificationEvent
import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
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
import com.ieum.planner.scheduler.DueSoonScheduler
import com.ieum.scheduler.SchedulerConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@RecordApplicationEvents
@Import(DueSoonSchedulerTest.FixedClock::class)
class DueSoonSchedulerTest @Autowired constructor(
	private val dueSoonScheduler: DueSoonScheduler,
	private val todoRepository: TodoRepository,
	private val todoChecklistItemRepository: TodoChecklistItemRepository,
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
	private val jdbcTemplate: JdbcTemplate,
) : PostgresTestSupport() {

	// 16:00:30 에 돌면 마감 / 시작이 (18:58, 19:00] 인 것을 찾는다.
	@TestConfiguration
	class FixedClock {
		@Bean
		@Primary
		fun fixedClock(): Clock =
			Clock.fixed(LocalDateTime.of(2026, 10, 30, 16, 0, 30).atZone(SchedulerConfig.SEOUL).toInstant(), SchedulerConfig.SEOUL)
	}

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	@Autowired
	private lateinit var events: ApplicationEvents

	private val familyId = UUID.randomUUID()
	private val mom = UUID.randomUUID()
	private val dad = UUID.randomUUID()
	private val kid = UUID.randomUUID()
	private val today = LocalDate.of(2026, 10, 30)

	@BeforeEach
	fun setUp() {
		todoChecklistItemRepository.deleteAll()
		todoRepository.deleteAllInBatch()
		scheduleAttendeeRepository.deleteAll()
		scheduleRepository.deleteAll()
		// ShedLock 은 한 번 만든 락 행을 기억하고 UPDATE 만 하므로, 행을 지우지 않고 만료만 시킨다. 락 시각은 UTC 로 저장된다.
		jdbcTemplate.update("UPDATE planner.shedlock SET lock_until = timezone('utc', now()) - interval '1 second'")
	}

	private fun published() = events.stream(NotificationEvent::class.java).toList()

	private fun todo(title: String, dueTime: LocalTime?, completed: Boolean = false, deleted: Boolean = false) =
		todoRepository.save(
			Todo(
				parentTodoId = null,
				familyId = familyId,
				creatorAccountId = mom,
				assigneeAccountId = dad,
				title = title,
				memo = null,
				visibility = Visibility.FAMILY,
				priority = TodoPriority.MEDIUM,
				dueDate = today,
				dueTime = dueTime,
			).apply {
				isCompleted = completed
				if (deleted) delete()
			},
		)

	private fun schedule(title: String, startAt: LocalDateTime, isAllDay: Boolean = false, attendees: List<UUID> = listOf(mom, dad)): Schedule {
		val schedule = scheduleRepository.save(
			Schedule(
				familyId = familyId,
				creatorAccountId = mom,
				title = title,
				memo = null,
				visibility = Visibility.FAMILY,
				isAllDay = isAllDay,
				startAt = startAt,
				endAt = startAt.plusHours(2),
			),
		)
		scheduleAttendeeRepository.saveAll(attendees.map { ScheduleAttendee(scheduleId = requireNotNull(schedule.scheduleId), accountId = it) })
		return schedule
	}

	@Test
	fun `마감 3시간 전이 된 미완료 할 일을 담당자에게 한 번씩 알린다`() {
		val dueAt7 = todo("분리수거하기", LocalTime.of(19, 0))
		val dueAt659 = todo("빨래 걷기", LocalTime.of(18, 59))
		todo("이미 지난 창", LocalTime.of(18, 58))
		todo("아직 이른 할 일", LocalTime.of(19, 1))
		todo("끝낸 할 일", LocalTime.of(19, 0), completed = true)
		todo("지운 할 일", LocalTime.of(19, 0), deleted = true)
		todo("시간 없는 할 일", null)

		dueSoonScheduler.run()

		fun expected(todo: Todo, at: LocalDateTime) = NotificationEvent(
			NotificationEventType.TODO_DUE_SOON,
			NotificationEventData(
				recipientAccountIds = listOf(dad),
				familyId = familyId,
				targetType = NotificationTargetType.TODO,
				targetId = todo.todoId,
				subject = todo.title,
				at = at,
				dedupKey = "TODO_DUE_SOON:${todo.todoId}:$at",
			),
		)
		assertEquals(
			setOf(expected(dueAt7, today.atTime(19, 0)), expected(dueAt659, today.atTime(18, 59))),
			published().toSet(),
		)
	}

	@Test
	fun `시작 3시간 전이 된 시간 일정을 참석자에게 알린다`() {
		val dinner = schedule("가족 외식", today.atTime(19, 0), attendees = listOf(mom, dad, kid))
		scheduleAttendeeRepository.findAllByScheduleIdInAndIsDeletedFalse(listOf(requireNotNull(dinner.scheduleId)))
			.single { it.accountId == kid }
			.also { it.delete() }
			.also(scheduleAttendeeRepository::save)
		schedule("종일 일정", today.atTime(19, 0), isAllDay = true)
		schedule("다른 시각 일정", today.atTime(19, 30))

		dueSoonScheduler.run()

		val at = today.atTime(19, 0)
		assertEquals(
			listOf(
				NotificationEvent(
					NotificationEventType.SCHEDULE_STARTING_SOON,
					NotificationEventData(
						recipientAccountIds = listOf(mom, dad),
						familyId = familyId,
						targetType = NotificationTargetType.SCHEDULE,
						targetId = dinner.scheduleId,
						subject = "가족 외식",
						at = at,
						dedupKey = "SCHEDULE_STARTING_SOON:${dinner.scheduleId}:$at",
					),
				),
			),
			published(),
		)
	}

	@Test
	fun `여러 Pod 가 함께 돌지 않도록 shedlock 테이블에 락을 남긴다`() {
		dueSoonScheduler.run()

		assertEquals(listOf("planner-due-soon"), jdbcTemplate.queryForList("SELECT name FROM planner.shedlock", String::class.java))
	}
}
