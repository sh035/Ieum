package com.ieum.planner.domain.entity

import com.ieum.planner.domain.enums.Visibility
import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "schedule")
class Schedule(
	@Column(updatable = false)
	val familyId: UUID?,

	@Column(nullable = false, updatable = false)
	val creatorAccountId: UUID,

	@Column(nullable = false)
	var title: String,

	var memo: String?,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	val visibility: Visibility,

	@Column(nullable = false)
	var isAllDay: Boolean,

	@Column(nullable = false)
	var startAt: LocalDateTime,

	@Column(nullable = false)
	var endAt: LocalDateTime,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val scheduleId: UUID? = null,
) : BaseEntity()
