package com.ieum.planner.service

import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventPublisher
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.planner.repository.ScheduleAttendeeRepository
import com.ieum.planner.repository.ScheduleRepository
import com.ieum.planner.repository.TodoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

// 예약을 걸어 두지 않고 돌 때마다 DB 현재 상태를 보므로, 완료 / 삭제 / 시간 변경이 자연히 반영된다.
@Service
class DueSoonService(
	private val todoRepository: TodoRepository,
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
	private val notificationEventPublisher: NotificationEventPublisher,
	private val clock: Clock,
) {

	// 마감 / 시작이 (지금 + 3시간 - 2분, 지금 + 3시간] 인 것을 알린다.
	// 서비스가 2분 넘게 내려가 있으면 그 사이 알림은 빠진다. 문제가 되면 보낸 시각을 기록하는 방식으로 바꾼다.
	@Transactional
	fun notifyDueSoon() {
		val windowEnd = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES).plus(LEAD_TIME)
		val windowStart = windowEnd.minus(WINDOW)

		todoRepository
			.findAllByIsCompletedFalseAndIsDeletedFalseAndDueTimeIsNotNullAndDueDateBetween(windowStart.toLocalDate(), windowEnd.toLocalDate())
			.forEach { todo ->
				val dueAt = requireNotNull(todo.dueDate).atTime(todo.dueTime)
				if (dueAt <= windowStart || dueAt > windowEnd) return@forEach
				notificationEventPublisher.publish(
					NotificationEventType.TODO_DUE_SOON,
					NotificationEventData(
						recipientAccountIds = listOf(todo.assigneeAccountId),
						familyId = todo.familyId,
						targetType = NotificationTargetType.TODO,
						targetId = todo.todoId,
						subject = todo.title,
						at = dueAt,
						dedupKey = "TODO_DUE_SOON:${todo.todoId}:$dueAt",
					),
				)
			}

		val schedules = scheduleRepository.findAllByIsAllDayFalseAndIsDeletedFalseAndStartAtGreaterThanAndStartAtLessThanEqual(windowStart, windowEnd)
		if (schedules.isEmpty()) return
		val attendees = scheduleAttendeeRepository
			.findAllByScheduleIdInAndIsDeletedFalse(schedules.map { requireNotNull(it.scheduleId) })
			.groupBy({ it.scheduleId }, { it.accountId })
		schedules.forEach { schedule ->
			notificationEventPublisher.publish(
				NotificationEventType.SCHEDULE_STARTING_SOON,
				NotificationEventData(
					recipientAccountIds = attendees[schedule.scheduleId].orEmpty(),
					familyId = schedule.familyId,
					targetType = NotificationTargetType.SCHEDULE,
					targetId = schedule.scheduleId,
					subject = schedule.title,
					at = schedule.startAt,
					dedupKey = "SCHEDULE_STARTING_SOON:${schedule.scheduleId}:${schedule.startAt}",
				),
			)
		}
	}

	companion object {
		private val LEAD_TIME = Duration.ofHours(3)
		private val WINDOW = Duration.ofMinutes(2)
	}
}
