package com.ieum.planner.service

import com.ieum.planner.controller.dto.response.BriefingCountResponse
import com.ieum.planner.repository.ScheduleAttendeeRepository
import com.ieum.planner.repository.ScheduleRepository
import com.ieum.planner.repository.TodoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

// 그날 데일리 브리핑에 들어갈 planner 몫을 계정별 개수로 센다.
@Service
class BriefingCountService(
	private val todoRepository: TodoRepository,
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
) {

	// 할 일은 담당자와 만든 사람 모두의 "내 것"이고, 일정은 참석자의 것이다.
	@Transactional(readOnly = true)
	fun countsOn(date: LocalDate): Map<UUID, BriefingCountResponse> {
		val counts = mutableMapOf<UUID, BriefingCountResponse>()
		fun add(accountId: UUID, change: (BriefingCountResponse) -> BriefingCountResponse) {
			counts[accountId] = change(counts[accountId] ?: BriefingCountResponse())
		}

		todoRepository.findAllByIsCompletedFalseAndIsDeletedFalseAndDueDateLessThanEqual(date).forEach { todo ->
			setOf(todo.assigneeAccountId, todo.creatorAccountId).forEach { accountId ->
				add(accountId) { if (todo.dueDate == date) it.copy(todo = it.todo + 1) else it.copy(overdueTodo = it.overdueTodo + 1) }
			}
		}

		val schedules = scheduleRepository.findAllByStartAtLessThanAndEndAtGreaterThanEqualAndIsDeletedFalse(
			date.plusDays(1).atStartOfDay(),
			date.atStartOfDay(),
		).filterNot { it.endsBefore(date.atStartOfDay()) }
		if (schedules.isNotEmpty()) {
			scheduleAttendeeRepository.findAllByScheduleIdInAndIsDeletedFalse(schedules.map { requireNotNull(it.scheduleId) })
				.forEach { attendee -> add(attendee.accountId) { it.copy(schedule = it.schedule + 1) } }
		}

		return counts
	}
}
