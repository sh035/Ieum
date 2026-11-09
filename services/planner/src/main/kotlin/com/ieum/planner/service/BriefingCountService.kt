package com.ieum.planner.service

import com.ieum.grpc.client.family.FamilyClient
import com.ieum.grpc.client.family.getMembersOf
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
	private val familyClient: FamilyClient,
) {

	// 할 일은 담당자와 만든 사람 모두의 "내 것"이고, 일정은 참석자의 것이다.
	// 떠난 사람의 몫은 세지 않는다. family 조회가 실패하면 일부만 센 숫자를 주지 않도록 그대로 실패시킨다.
	@Transactional(readOnly = true)
	fun countsOn(date: LocalDate): Map<UUID, BriefingCountResponse> {
		val todos = todoRepository.findAllByIsCompletedFalseAndIsDeletedFalseAndDueDateLessThanEqual(date)
		val schedules = scheduleRepository.findAllByStartAtLessThanAndEndAtGreaterThanEqualAndIsDeletedFalse(
			date.plusDays(1).atStartOfDay(),
			date.atStartOfDay(),
		).filterNot { it.endsBefore(date.atStartOfDay()) }
		val members = familyClient.getMembersOf((todos.map { it.familyId } + schedules.map { it.familyId }).filterNotNull())

		val counts = mutableMapOf<UUID, BriefingCountResponse>()
		fun add(familyId: UUID?, accountId: UUID, change: (BriefingCountResponse) -> BriefingCountResponse) {
			if (familyId == null || accountId in members[familyId].orEmpty()) {
				counts[accountId] = change(counts[accountId] ?: BriefingCountResponse())
			}
		}

		todos.forEach { todo ->
			setOf(todo.assigneeAccountId, todo.creatorAccountId).forEach { accountId ->
				add(todo.familyId, accountId) { if (todo.dueDate == date) it.copy(todo = it.todo + 1) else it.copy(overdueTodo = it.overdueTodo + 1) }
			}
		}

		if (schedules.isNotEmpty()) {
			val familyIds = schedules.associate { it.scheduleId to it.familyId }
			scheduleAttendeeRepository.findAllByScheduleIdInAndIsDeletedFalse(schedules.map { requireNotNull(it.scheduleId) })
				.forEach { attendee -> add(familyIds[attendee.scheduleId], attendee.accountId) { it.copy(schedule = it.schedule + 1) } }
		}

		return counts
	}
}
