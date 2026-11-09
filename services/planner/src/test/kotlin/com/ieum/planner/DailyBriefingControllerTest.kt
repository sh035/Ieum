package com.ieum.planner

import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.planner.domain.entity.Schedule
import com.ieum.planner.domain.entity.ScheduleAttendee
import com.ieum.planner.domain.entity.Todo
import com.ieum.planner.domain.enums.TodoPriority
import com.ieum.planner.domain.enums.Visibility
import com.ieum.planner.repository.ScheduleAttendeeRepository
import com.ieum.planner.repository.ScheduleRepository
import com.ieum.planner.repository.TodoRepository
import com.ieum.web.exception.IeumException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class DailyBriefingControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val todoRepository: TodoRepository,
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val familyId = UUID.randomUUID()
	private val otherFamilyId = UUID.randomUUID()
	private val leftFamilyId = UUID.randomUUID()
	private val me = UUID.randomUUID()
	private val member = UUID.randomUUID()
	private val today = LocalDate.now()

	@BeforeEach
	fun setUp() {
		scheduleAttendeeRepository.deleteAllInBatch()
		scheduleRepository.deleteAllInBatch()
		todoRepository.deleteAllInBatch()
		`when`(familyClient.getFamilyIds(me)).thenReturn(listOf(familyId, otherFamilyId))
	}

	private fun todo(
		title: String,
		dueDate: LocalDate?,
		dueTime: LocalTime? = null,
		family: UUID? = familyId,
		creator: UUID = me,
		assignee: UUID = me,
	) = todoRepository.save(
		Todo(
			parentTodoId = null,
			familyId = family,
			creatorAccountId = creator,
			assigneeAccountId = assignee,
			title = title,
			memo = null,
			visibility = if (family == null) Visibility.PRIVATE else Visibility.FAMILY,
			priority = TodoPriority.NONE,
			dueDate = dueDate,
			dueTime = dueTime,
		),
	)

	private fun schedule(
		title: String,
		startAt: LocalDateTime,
		endAt: LocalDateTime,
		family: UUID? = familyId,
		isAllDay: Boolean = false,
		attendee: UUID = me,
	) {
		val saved = scheduleRepository.save(
			Schedule(
				familyId = family,
				creatorAccountId = attendee,
				title = title,
				memo = null,
				visibility = if (family == null) Visibility.PRIVATE else Visibility.FAMILY,
				isAllDay = isAllDay,
				startAt = startAt,
				endAt = endAt,
			),
		)
		scheduleAttendeeRepository.save(ScheduleAttendee(scheduleId = requireNotNull(saved.scheduleId), accountId = attendee))
	}

	private fun briefing(accountId: UUID = me) = mockMvc.get("/api/v1/daily-briefing") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	@Test
	fun `오늘 할 일은 임박순으로 밀린 할 일은 따로 돌려준다`() {
		todo("시간 없음", today)
		todo("저녁", today, LocalTime.of(19, 0))
		todo("아침", today, LocalTime.of(8, 0))
		todo("개인 할 일", today, LocalTime.of(20, 0), family = null)
		todo("어제 밀림", today.minusDays(1))
		todo("사흘 밀림", today.minusDays(3))

		briefing().andExpect {
			status { isOk() }
			jsonPath("\$.data.todos.length()") { value(4) }
			jsonPath("\$.data.todos[0].title") { value("아침") }
			jsonPath("\$.data.todos[1].title") { value("저녁") }
			jsonPath("\$.data.todos[2].title") { value("개인 할 일") }
			jsonPath("\$.data.todos[3].title") { value("시간 없음") }
			jsonPath("\$.data.overdueTodos.length()") { value(2) }
			jsonPath("\$.data.overdueTodos[0].title") { value("사흘 밀림") }
			jsonPath("\$.data.overdueTodos[1].title") { value("어제 밀림") }
		}
	}

	@Test
	fun `내가 맡았거나 만든 할 일만 관계와 함께 여러 가족에 걸쳐 나온다`() {
		todo("내 일", today)
		todo("맡긴 일", today.minusDays(1), family = otherFamilyId, assignee = member)
		todo("맡은 일", today.minusDays(2), creator = member)
		todo("남의 일", today, creator = member, assignee = member)

		briefing().andExpect {
			jsonPath("\$.data.todos.length()") { value(1) }
			jsonPath("\$.data.todos[0].relation") { value("MINE") }
			jsonPath("\$.data.overdueTodos.length()") { value(2) }
			jsonPath("\$.data.overdueTodos[0].relation") { value("ASSIGNED_TO_ME") }
			jsonPath("\$.data.overdueTodos[1].relation") { value("ASSIGNED_BY_ME") }
		}
	}

	@Test
	fun `완료 삭제 내일 마감 기한 없는 할 일은 빠진다`() {
		todoRepository.save(todo("완료", today).apply { isCompleted = true })
		todoRepository.save(todo("삭제", today).apply { delete() })
		todo("내일", today.plusDays(1))
		todo("기한 없음", null)

		briefing().andExpect {
			jsonPath("\$.data.todos") { isEmpty() }
			jsonPath("\$.data.overdueTodos") { isEmpty() }
		}
	}

	@Test
	fun `오늘과 겹치는 내 일정을 시작순으로 돌려준다`() {
		val midnight = today.atStartOfDay()
		schedule("저녁 약속", midnight.plusHours(19), midnight.plusHours(21))
		schedule("종일 일정", midnight, midnight, isAllDay = true)
		schedule("어제부터 이어짐", midnight.minusHours(2), midnight.plusHours(1))
		schedule("개인 일정", midnight.plusHours(10), midnight.plusHours(11), family = null)
		schedule("자정에 끝난 어제 일정", midnight.minusHours(2), midnight)
		schedule("내일 일정", midnight.plusDays(1), midnight.plusDays(1).plusHours(1))
		schedule("남의 일정", midnight.plusHours(9), midnight.plusHours(10), attendee = member)

		briefing().andExpect {
			jsonPath("\$.data.schedules.length()") { value(4) }
			jsonPath("\$.data.schedules[0].title") { value("어제부터 이어짐") }
			jsonPath("\$.data.schedules[1].title") { value("종일 일정") }
			jsonPath("\$.data.schedules[2].title") { value("개인 일정") }
			jsonPath("\$.data.schedules[3].title") { value("저녁 약속") }
		}
	}

	@Test
	fun `떠난 가족의 할 일과 일정은 이벤트 처리 전이어도 나오지 않는다`() {
		val midnight = today.atStartOfDay()
		todo("떠난 가족 할 일", today, family = leftFamilyId)
		schedule("떠난 가족 일정", midnight.plusHours(9), midnight.plusHours(10), family = leftFamilyId)

		briefing().andExpect {
			jsonPath("\$.data.todos") { isEmpty() }
			jsonPath("\$.data.schedules") { isEmpty() }
		}
	}

	@Test
	fun `가족이 없어도 개인 할 일과 일정은 나온다`() {
		val alone = UUID.randomUUID()
		val midnight = today.atStartOfDay()
		`when`(familyClient.getFamilyIds(alone)).thenReturn(emptyList())
		todo("개인 할 일", today, family = null, creator = alone, assignee = alone)
		schedule("개인 일정", midnight.plusHours(9), midnight.plusHours(10), family = null, attendee = alone)

		briefing(alone).andExpect {
			status { isOk() }
			jsonPath("\$.data.todos.length()") { value(1) }
			jsonPath("\$.data.overdueTodos") { isEmpty() }
			jsonPath("\$.data.schedules.length()") { value(1) }
		}
	}

	@Test
	fun `family 를 부를 수 없으면 일부만 주지 않고 503`() {
		`when`(familyClient.getFamilyIds(me)).thenThrow(IeumException(GrpcClientErrorCode.FAMILY_SERVICE_UNAVAILABLE))
		todo("내 일", today)

		briefing().andExpect { status { isServiceUnavailable() } }
	}
}
