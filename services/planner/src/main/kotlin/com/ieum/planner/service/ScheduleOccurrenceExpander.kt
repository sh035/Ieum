package com.ieum.planner.service

import com.ieum.planner.domain.entity.Schedule
import com.ieum.planner.repository.ScheduleRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

// 반복 원본을 기간 안의 회차로 펼친다. 일반 일정과 회차를 대신하는 행은 기간과 겹치면 그대로 둔다.
@Component
class ScheduleOccurrenceExpander(
	private val scheduleRepository: ScheduleRepository,
) {

	// schedule 은 반복 원본이거나 일반 일정 / 대신하는 행이다. 응답의 scheduleId 와 참석자는 schedule 을 따른다.
	data class Occurrence(val schedule: Schedule, val startAt: LocalDateTime, val endAt: LocalDateTime)

	// 따로 바꾸거나 취소한 회차는 대신하는 행이 맡으므로 원본 회차에서 뺀다.
	fun expand(schedules: Collection<Schedule>, rangeStart: LocalDateTime, rangeEnd: LocalDateTime): List<Occurrence> {
		val candidates = schedules.distinctBy { it.scheduleId }
		val masterIds = candidates.filter { it.repeatType != null }.map { requireNotNull(it.scheduleId) }
		val replaced: Map<UUID, Set<LocalDateTime>> = if (masterIds.isEmpty()) {
			emptyMap()
		} else {
			scheduleRepository.findAllByRecurringScheduleIdIn(masterIds)
				.groupBy({ requireNotNull(it.recurringScheduleId) }, { requireNotNull(it.originalStartAt) })
				.mapValues { it.value.toSet() }
		}

		return candidates.flatMap { schedule ->
			val skip = replaced[schedule.scheduleId].orEmpty()
			schedule.occurrenceStartsIn(rangeStart, rangeEnd)
				.filterNot { it in skip }
				.map { Occurrence(schedule, it, schedule.occurrenceEnd(it)) }
		}.sortedWith(compareBy({ it.startAt }, { it.schedule.createdAt }))
	}
}
