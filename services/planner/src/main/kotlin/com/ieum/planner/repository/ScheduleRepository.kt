package com.ieum.planner.repository

import com.ieum.planner.domain.entity.Schedule
import com.ieum.planner.domain.enums.Visibility
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface ScheduleRepository : JpaRepository<Schedule, UUID> {

	fun findByScheduleIdAndIsDeletedFalse(scheduleId: UUID): Schedule?

	fun findAllByFamilyIdAndStartAtLessThanAndEndAtGreaterThanEqualAndIsDeletedFalse(
		familyId: UUID,
		rangeEnd: LocalDateTime,
		rangeStart: LocalDateTime,
	): List<Schedule>

	fun findAllByCreatorAccountIdAndVisibilityAndStartAtLessThanAndEndAtGreaterThanEqualAndIsDeletedFalse(
		creatorAccountId: UUID,
		visibility: Visibility,
		rangeEnd: LocalDateTime,
		rangeStart: LocalDateTime,
	): List<Schedule>

	// 3시간 전 알림 후보다. 종일 일정은 시각이 없어 브리핑으로만 알린다.
	fun findAllByIsAllDayFalseAndIsDeletedFalseAndStartAtGreaterThanAndStartAtLessThanEqual(
		from: LocalDateTime,
		to: LocalDateTime,
	): List<Schedule>

	// 브리핑 후보다. 기간이 겹치는 모든 일정을 본다.
	fun findAllByStartAtLessThanAndEndAtGreaterThanEqualAndIsDeletedFalse(
		rangeEnd: LocalDateTime,
		rangeStart: LocalDateTime,
	): List<Schedule>
}
