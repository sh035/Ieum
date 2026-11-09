package com.ieum.planner

import com.ieum.events.NotificationEvent
import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
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
import com.ieum.planner.scheduler.DueSoonScheduler
import com.ieum.planner.service.DueSoonService
import com.ieum.scheduler.SchedulerCheckpointRepository
import com.ieum.scheduler.ShedlockRepository
import com.ieum.web.config.TimeZoneConfig
import com.ieum.web.exception.IeumException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@RecordApplicationEvents
@Import(DueSoonSchedulerTest.TestClock::class)
class DueSoonSchedulerTest @Autowired constructor(
	private val dueSoonScheduler: DueSoonScheduler,
	private val dueSoonService: DueSoonService,
	private val clock: MovableClock,
	private val todoRepository: TodoRepository,
	private val todoChecklistItemRepository: TodoChecklistItemRepository,
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
	private val schedulerCheckpointRepository: SchedulerCheckpointRepository,
	private val shedlockRepository: ShedlockRepository,
) : PostgresTestSupport() {

	// 회차마다 시각을 옮겨 가며 이어서 처리하는지 본다.
	class MovableClock(var now: LocalDateTime) : Clock() {
		override fun getZone(): ZoneId = TimeZoneConfig.SEOUL
		override fun withZone(zone: ZoneId): Clock = this
		override fun instant(): Instant = now.atZone(TimeZoneConfig.SEOUL).toInstant()
	}

	@TestConfiguration
	class TestClock {
		@Bean
		@Primary
		fun movableClock() = MovableClock(LocalDateTime.of(2026, 10, 30, 16, 0, 30))
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
		schedulerCheckpointRepository.deleteAll()
		// ShedLock 은 한 번 만든 락 행을 기억하고 UPDATE 만 하므로, 행을 지우지 않고 만료만 시킨다.
		shedlockRepository.saveAll(shedlockRepository.findAll().onEach { it.lockUntil = LocalDateTime.of(2000, 1, 1, 0, 0) })
		members(mom, dad, kid)
	}

	private fun members(vararg accountIds: UUID) {
		`when`(familyClient.getFamily(familyId)).thenReturn(FamilyInfo("우리집", accountIds.associateWith { FamilyRole.MEMBER }))
	}

	private fun runAt(hour: Int, minute: Int, date: LocalDate = today) {
		clock.now = date.atTime(hour, minute, 30)
		dueSoonService.notifyDueSoon()
	}

	private fun alertedTitles() = events.stream(NotificationEvent::class.java).toList().map { requireNotNull(it.data.subject) }

	private fun todo(
		title: String,
		dueTime: LocalTime?,
		dueDate: LocalDate = today,
		completed: Boolean = false,
		deleted: Boolean = false,
		visibility: Visibility = Visibility.FAMILY,
	) =
		todoRepository.save(
			Todo(
				parentTodoId = null,
				familyId = if (visibility == Visibility.FAMILY) familyId else null,
				creatorAccountId = mom,
				assigneeAccountId = dad,
				title = title,
				memo = null,
				visibility = visibility,
				priority = TodoPriority.MEDIUM,
				dueDate = dueDate,
				dueTime = dueTime,
			).apply {
				isCompleted = completed
				if (deleted) delete()
			},
		)

	private fun schedule(
		title: String,
		startAt: LocalDateTime,
		isAllDay: Boolean = false,
		deleted: Boolean = false,
		attendees: List<UUID> = listOf(mom, dad),
	): Schedule {
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
			).apply { if (deleted) delete() },
		)
		scheduleAttendeeRepository.saveAll(attendees.map { ScheduleAttendee(scheduleId = requireNotNull(schedule.scheduleId), accountId = it) })
		return schedule
	}

	@Test
	fun `처음 돌면 최대 10분 전까지 거슬러 마감 3시간 전이 된 미완료 할 일을 담당자에게 알린다`() {
		val dueAt7 = todo("분리수거하기", LocalTime.of(19, 0))
		todo("빨래 걷기", LocalTime.of(18, 51))
		todo("이미 지난 시점", LocalTime.of(18, 50))
		todo("아직 이른 할 일", LocalTime.of(19, 1))
		todo("끝낸 할 일", LocalTime.of(19, 0), completed = true)
		todo("지운 할 일", LocalTime.of(19, 0), deleted = true)
		todo("시간 없는 할 일", null)

		runAt(16, 0)

		assertEquals(listOf("분리수거하기", "빨래 걷기"), alertedTitles().sorted())
		val at = today.atTime(19, 0)
		assertEquals(
			NotificationEvent(
				NotificationEventType.TODO_DUE_SOON,
				NotificationEventData(
					recipientAccountIds = listOf(dad),
					familyId = familyId,
					targetType = NotificationTargetType.TODO,
					targetId = dueAt7.todoId,
					subject = "분리수거하기",
					at = at,
					dedupKey = "TODO_DUE_SOON:${dueAt7.todoId}:$at",
				),
			),
			events.stream(NotificationEvent::class.java).toList().single { it.data.subject == "분리수거하기" },
		)
	}

	@Test
	fun `다음 회차는 앞 회차가 처리한 지점부터 이어서 처리해 같은 할 일을 두 번 보내지 않는다`() {
		todo("19시 할 일", LocalTime.of(19, 0))
		todo("19시 1분 할 일", LocalTime.of(19, 1))
		todo("19시 2분 할 일", LocalTime.of(19, 2))

		runAt(16, 0)
		runAt(16, 1)
		runAt(16, 1)
		runAt(16, 2)

		assertEquals(listOf("19시 1분 할 일", "19시 2분 할 일", "19시 할 일"), alertedTitles().sorted())
	}

	@Test
	fun `잠깐 멈췄다 살아나면 밀린 구간을 따라잡고 10분보다 오래 멈춘 구간은 건너뛴다`() {
		todo("19시 3분 할 일", LocalTime.of(19, 3))
		todo("19시 20분 할 일", LocalTime.of(19, 20))
		todo("19시 35분 할 일", LocalTime.of(19, 35))

		runAt(16, 0)
		runAt(16, 5)
		runAt(16, 40)

		assertEquals(listOf("19시 35분 할 일", "19시 3분 할 일"), alertedTitles().sorted())
	}

	@Test
	fun `자정을 넘는 구간도 다음 날 마감을 놓치지 않는다`() {
		todo("오늘 밤 할 일", LocalTime.of(23, 55))
		todo("내일 자정 할 일", LocalTime.MIDNIGHT, dueDate = today.plusDays(1))
		todo("내일 아침 할 일", LocalTime.of(0, 1), dueDate = today.plusDays(1))

		runAt(21, 0)

		assertEquals(listOf("내일 자정 할 일", "오늘 밤 할 일"), alertedTitles().sorted())
	}

	@Test
	fun `시작 3시간 전이 된 시간 일정을 남아 있는 참석자에게 알린다`() {
		val dinner = schedule("가족 외식", today.atTime(19, 0), attendees = listOf(mom, dad, kid))
		scheduleAttendeeRepository.findAllByScheduleIdInAndIsDeletedFalse(listOf(requireNotNull(dinner.scheduleId)))
			.single { it.accountId == kid }
			.also { it.delete() }
			.also(scheduleAttendeeRepository::save)
		schedule("10분 전 경계 안 일정", today.atTime(18, 51))
		schedule("10분 전 경계 밖 일정", today.atTime(18, 50))
		schedule("아직 이른 일정", today.atTime(19, 1))
		schedule("종일 일정", today.atTime(19, 0), isAllDay = true)
		schedule("지운 일정", today.atTime(19, 0), deleted = true)

		runAt(16, 0)

		assertEquals(listOf("10분 전 경계 안 일정", "가족 외식"), alertedTitles().sorted())
		val event = events.stream(NotificationEvent::class.java).toList().single { it.data.subject == "가족 외식" }
		val at = today.atTime(19, 0)
		assertEquals(
			NotificationEvent(
				NotificationEventType.SCHEDULE_STARTING_SOON,
				NotificationEventData(
					recipientAccountIds = listOf(mom, dad).sorted(),
					familyId = familyId,
					targetType = NotificationTargetType.SCHEDULE,
					targetId = dinner.scheduleId,
					subject = "가족 외식",
					at = at,
					dedupKey = "SCHEDULE_STARTING_SOON:${dinner.scheduleId}:$at",
				),
			),
			event.copy(data = event.data.copy(recipientAccountIds = event.data.recipientAccountIds.sorted())),
		)
	}

	@Test
	fun `가족을 떠난 담당자에게는 할 일을 알리지 않는다`() {
		members(mom, kid)
		todo("떠난 사람 할 일", LocalTime.of(19, 0))

		runAt(16, 0)

		assertEquals(emptyList(), alertedTitles())
	}

	@Test
	fun `가족을 떠난 참석자는 빼고 남은 참석자에게만 일정을 알린다`() {
		members(mom)
		schedule("가족 외식", today.atTime(19, 0), attendees = listOf(mom, dad))
		schedule("떠난 사람만 참석", today.atTime(18, 55), attendees = listOf(dad))

		runAt(16, 0)

		val event = events.stream(NotificationEvent::class.java).toList().single()
		assertEquals("가족 외식", event.data.subject)
		assertEquals(listOf(mom), event.data.recipientAccountIds)
	}

	@Test
	fun `개인 할 일은 가족 조회 없이 알린다`() {
		todo("개인 할 일", LocalTime.of(19, 0), visibility = Visibility.PRIVATE)

		runAt(16, 0)

		assertEquals(listOf("개인 할 일"), alertedTitles())
		verify(familyClient, never()).getFamily(familyId)
	}

	@Test
	fun `같은 가족 항목이 여러 개여도 가족 조회는 한 번이다`() {
		todo("19시 할 일", LocalTime.of(19, 0))
		schedule("가족 외식", today.atTime(19, 0))

		runAt(16, 0)

		verify(familyClient, times(1)).getFamily(familyId)
	}

	@Test
	fun `구간에 알릴 항목이 없으면 가족을 조회하지 않는다`() {
		todo("아직 이른 할 일", LocalTime.of(20, 0))

		runAt(16, 0)

		verify(familyClient, never()).getFamily(familyId)
	}

	@Test
	fun `family 를 부를 수 없으면 알리지 않고 다음 회차에 이어서 처리한다`() {
		runAt(16, 0)
		todo("19시 1분 할 일", LocalTime.of(19, 1))
		`when`(familyClient.getFamily(familyId)).thenThrow(IeumException(GrpcClientErrorCode.FAMILY_SERVICE_UNAVAILABLE))

		assertFailsWith<IeumException> { runAt(16, 1) }
		assertEquals(emptyList(), alertedTitles())

		reset(familyClient)
		members(mom, dad, kid)
		runAt(16, 2)

		assertEquals(listOf("19시 1분 할 일"), alertedTitles())
	}

	@Test
	fun `여러 Pod 가 함께 돌지 않도록 shedlock 테이블에 락을 남긴다`() {
		dueSoonScheduler.run()

		assertEquals(listOf("planner-due-soon"), shedlockRepository.findAll().map { it.name })
	}
}
