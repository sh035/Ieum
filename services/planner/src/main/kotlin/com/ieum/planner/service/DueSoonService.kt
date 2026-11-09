package com.ieum.planner.service

import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventPublisher
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.grpc.client.family.getMembersOf
import com.ieum.planner.domain.entity.Todo
import com.ieum.planner.repository.ScheduleAttendeeRepository
import com.ieum.planner.repository.ScheduleRepository
import com.ieum.planner.repository.TodoRepository
import com.ieum.scheduler.SchedulerCheckpointService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

// 예약을 걸어 두지 않고 돌 때마다 DB 현재 상태를 보므로, 완료 / 삭제 / 시간 변경이 자연히 반영된다.
@Service
class DueSoonService(
	private val todoRepository: TodoRepository,
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
	private val notificationEventPublisher: NotificationEventPublisher,
	private val schedulerCheckpointService: SchedulerCheckpointService,
	private val familyClient: FamilyClient,
	private val clock: Clock,
) {

	// 마감 / 시작이 (앞 회차가 처리한 지점, 지금 + 3시간] 인 것을 알린다.
	// 10분보다 오래 멈췄다 살아나면 그 사이 알림은 건너뛴다. 3시간 전 시점이 이미 지난 알림은 보내지 않는다.
	@Transactional
	fun notifyDueSoon() {
		val until = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES).plus(LEAD_TIME)
		schedulerCheckpointService.process(CHECKPOINT, until, MAX_CATCH_UP) { windowStart, windowEnd -> notify(windowStart, windowEnd) }
	}

	// 떠난 사람에게는 보내지 않는다. family 조회가 실패하면 회차 전체를 되돌려, 10분 안에 복구되면 다음 회차가 이어서 처리한다.
	private fun notify(windowStart: LocalDateTime, windowEnd: LocalDateTime) {
		val todos = todoRepository
			.findAllByIsCompletedFalseAndIsDeletedFalseAndDueTimeIsNotNullAndDueDateBetween(windowStart.toLocalDate(), windowEnd.toLocalDate())
			.filter { it.dueAt() > windowStart && it.dueAt() <= windowEnd }
		val schedules = scheduleRepository.findAllByIsAllDayFalseAndIsDeletedFalseAndStartAtGreaterThanAndStartAtLessThanEqual(windowStart, windowEnd)
		val attendees = if (schedules.isEmpty()) {
			emptyMap()
		} else {
			scheduleAttendeeRepository
				.findAllByScheduleIdInAndIsDeletedFalse(schedules.map { requireNotNull(it.scheduleId) })
				.groupBy({ it.scheduleId }, { it.accountId })
		}
		val members = familyClient.getMembersOf((todos.map { it.familyId } + schedules.map { it.familyId }).filterNotNull())
		fun currentMembers(familyId: UUID?, accountIds: List<UUID>) =
			if (familyId == null) accountIds else accountIds.filter { it in members[familyId].orEmpty() }

		todos.forEach { todo ->
			val dueAt = todo.dueAt()
			notificationEventPublisher.publish(
				NotificationEventType.TODO_DUE_SOON,
				NotificationEventData(
					recipientAccountIds = currentMembers(todo.familyId, listOf(todo.assigneeAccountId)),
					familyId = todo.familyId,
					targetType = NotificationTargetType.TODO,
					targetId = todo.todoId,
					subject = todo.title,
					at = dueAt,
					dedupKey = "TODO_DUE_SOON:${todo.todoId}:$dueAt",
				),
			)
		}

		schedules.forEach { schedule ->
			notificationEventPublisher.publish(
				NotificationEventType.SCHEDULE_STARTING_SOON,
				NotificationEventData(
					recipientAccountIds = currentMembers(schedule.familyId, attendees[schedule.scheduleId].orEmpty()),
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

	private fun Todo.dueAt() = requireNotNull(dueDate).atTime(dueTime)

	companion object {
		private const val CHECKPOINT = "planner-due-soon"
		private val LEAD_TIME = Duration.ofHours(3)
		private val MAX_CATCH_UP = Duration.ofMinutes(10)
	}
}
