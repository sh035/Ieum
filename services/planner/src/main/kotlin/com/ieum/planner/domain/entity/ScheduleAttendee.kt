package com.ieum.planner.domain.entity

import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.util.UUID

@Entity
@Table(name = "schedule_attendee")
class ScheduleAttendee(
	@Column(nullable = false, updatable = false)
	val scheduleId: UUID,

	@Column(nullable = false, updatable = false)
	val accountId: UUID,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val scheduleAttendeeId: UUID? = null,
) : BaseEntity()
