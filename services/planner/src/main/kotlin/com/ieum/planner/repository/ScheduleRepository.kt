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
}
