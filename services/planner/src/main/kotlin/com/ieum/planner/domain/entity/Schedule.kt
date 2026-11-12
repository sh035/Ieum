package com.ieum.planner.domain.entity

import com.ieum.planner.domain.enums.RepeatType
import com.ieum.planner.domain.enums.Visibility
import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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

	// 반복 원본이면 규칙이 있다. startAt / endAt 이 첫 회차다.
	@Enumerated(EnumType.STRING)
	var repeatType: RepeatType? = null,

	var repeatUntil: LocalDate? = null,

	// 반복의 한 회차를 대신하는 행이면 원본과 원래 회차 시작이 있다.
	@Column(updatable = false)
	val recurringScheduleId: UUID? = null,

	@Column(updatable = false)
	val originalStartAt: LocalDateTime? = null,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val scheduleId: UUID? = null,
) : BaseEntity() {

	// [rangeStart, rangeEnd) 와 겹치는 회차의 시작들이다. 반복 원본이 아니면 자기 자신 하나를 본다.
	fun occurrenceStartsIn(rangeStart: LocalDateTime, rangeEnd: LocalDateTime): List<LocalDateTime> {
		val type = repeatType ?: return listOf(startAt).filter { overlaps(it, rangeStart, rangeEnd) }
		val length = Duration.between(startAt, endAt)
		// 앞쪽 회차를 하나씩 세지 않게 기간 시작 근처부터 본다. 월 계산은 말일 보정 때 적게 셀 뿐이라 건너뛰는 회차가 없다.
		var index = maxOf(0L, unitsBetween(type, startAt, rangeStart.minus(length)))
		val starts = mutableListOf<LocalDateTime>()
		while (true) {
			val start = type.occurrence(startAt, index)
			if (start >= rangeEnd || repeatUntil?.let { start.toLocalDate().isAfter(it) } == true) {
				return starts
			}
			if (overlaps(start, rangeStart, rangeEnd)) {
				starts += start
			}
			index++
		}
	}

	fun isOccurrence(start: LocalDateTime) = start in occurrenceStartsIn(start, start.plusNanos(1))

	fun occurrenceEnd(start: LocalDateTime): LocalDateTime = start.plus(Duration.between(startAt, endAt))

	private fun overlaps(start: LocalDateTime, rangeStart: LocalDateTime, rangeEnd: LocalDateTime): Boolean {
		val end = occurrenceEnd(start)
		return start < rangeEnd && end >= rangeStart && !endsBefore(start, end, rangeStart)
	}

	// 구글 / 애플 캘린더처럼 시각 일정의 끝 시각은 포함하지 않는다. 자정에 끝나는 일정은 전날 일정이다.
	private fun endsBefore(start: LocalDateTime, end: LocalDateTime, rangeStart: LocalDateTime) = !isAllDay && start < rangeStart && end <= rangeStart

	private fun unitsBetween(type: RepeatType, from: LocalDateTime, to: LocalDateTime): Long = when (type) {
		RepeatType.DAILY -> ChronoUnit.DAYS.between(from, to)
		RepeatType.WEEKLY -> ChronoUnit.WEEKS.between(from, to)
		RepeatType.MONTHLY -> ChronoUnit.MONTHS.between(from, to)
	}
}
