package com.ieum.planner.service

import com.ieum.planner.controller.dto.response.CalendarResponse
import com.ieum.planner.exception.PlannerErrorCode
import com.ieum.web.exception.IeumException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class CalendarService(
	private val todoService: TodoService,
	private val scheduleService: ScheduleService,
	private val familyClient: FamilyClient,
) {

	// 날짜 칸 배치는 클라이언트가 한다. 서버는 기간 안의 할 일(마감일)과 일정(기간 겹침)만 준다.
	@Transactional(readOnly = true)
	fun getCalendar(accountId: String, familyId: UUID?, from: LocalDate, to: LocalDate): CalendarResponse {
		if (to.isBefore(from)) {
			throw IeumException(PlannerErrorCode.INVALID_DATE_RANGE)
		}
		// 월 보기 6주(42칸)까지 허용한다.
		if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
			throw IeumException(PlannerErrorCode.CALENDAR_RANGE_TOO_LONG)
		}
		val me = UUID.fromString(accountId)
		if (familyId != null && !familyClient.isMember(familyId, me)) {
			throw IeumException(PlannerErrorCode.FAMILY_NOT_FOUND)
		}

		return CalendarResponse(
			todos = todoService.findTodosDueBetween(me, familyId, from, to),
			schedules = scheduleService.findSchedules(me, familyId, from, to),
		)
	}

	companion object {
		private const val MAX_RANGE_DAYS = 42L
	}
}
