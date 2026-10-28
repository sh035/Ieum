package com.ieum.planner.repository

import com.ieum.planner.domain.entity.ScheduleAttendee
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ScheduleAttendeeRepository : JpaRepository<ScheduleAttendee, UUID> {

	fun findAllByScheduleIdInAndIsDeletedFalse(scheduleIds: Collection<UUID>): List<ScheduleAttendee>
}
