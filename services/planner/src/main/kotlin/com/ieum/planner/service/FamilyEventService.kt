package com.ieum.planner.service

import com.ieum.planner.repository.ScheduleAttendeeRepository
import com.ieum.planner.repository.ScheduleRepository
import com.ieum.planner.repository.TodoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

// family 의 outbox 이벤트는 두 번 올 수 있어서 다시 처리해도 결과가 같게 만든다.
// 체크리스트와 참석자는 직접 삭제할 때처럼 그대로 두고, 할 일 / 일정이 지워지면 함께 보이지 않는다.
@Service
class FamilyEventService(
	private val todoRepository: TodoRepository,
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// 떠난 사람이 만들었거나 맡은 할 일과 그 하위 할 일, 만든 일정, 다른 사람 일정의 참석을 지운다.
	// 다시 들어와 새로 만든 것이 늦게 온 같은 이벤트로 지워지지 않게, 떠난 시각 전에 만든 것만 지운다.
	@Transactional
	fun deleteMemberData(familyId: UUID, accountId: UUID, leftAt: LocalDateTime) {
		val todos = todoRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId)
		val todoIds = todos
			.filter { (it.creatorAccountId == accountId || it.assigneeAccountId == accountId) && !it.createdAt.isAfter(leftAt) }
			.mapTo(mutableSetOf()) { requireNotNull(it.todoId) }
		// 하위 할 일은 같은 가족에 있으므로 이미 읽은 목록에서 부모를 따라 내려간다.
		do {
			val children = todos.filter { it.parentTodoId in todoIds && it.todoId !in todoIds }
			children.forEach { todoIds += requireNotNull(it.todoId) }
		} while (children.isNotEmpty())
		todos.filter { it.todoId in todoIds }.forEach { it.delete() }

		val schedules = scheduleRepository.findAllByFamilyIdAndIsDeletedFalse(familyId)
		val leaversSchedules = schedules.filter { it.creatorAccountId == accountId && !it.createdAt.isAfter(leftAt) }
		leaversSchedules.forEach { it.delete() }
		val attendees = if (schedules.isEmpty()) {
			emptyList()
		} else {
			scheduleAttendeeRepository.findAllByScheduleIdInAndIsDeletedFalse(schedules.map { requireNotNull(it.scheduleId) })
				.filter { it.accountId == accountId && !it.createdAt.isAfter(leftAt) }
		}
		attendees.forEach { it.delete() }

		log.debug(
			"Planner member data deleted. familyId={}, accountId={}, todos={}, schedules={}, attendees={}",
			familyId,
			accountId,
			todoIds.size,
			leaversSchedules.size,
			attendees.size,
		)
	}

	@Transactional
	fun deleteFamily(familyId: UUID) {
		todoRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId).forEach { it.delete() }
		scheduleRepository.findAllByFamilyIdAndIsDeletedFalse(familyId).forEach { it.delete() }

		log.debug("Planner family data deleted. familyId={}", familyId)
	}
}
