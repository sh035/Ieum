package com.ieum.planner.repository

import com.ieum.planner.domain.entity.Schedule
import com.ieum.planner.domain.enums.Visibility
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime
import java.util.UUID

interface ScheduleRepository : JpaRepository<Schedule, UUID> {

	fun findByScheduleIdAndIsDeletedFalse(scheduleId: UUID): Schedule?

	fun findAllByFamilyIdAndIsDeletedFalse(familyId: UUID): List<Schedule>

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

	// 반복 원본 후보다. 첫 회차가 기간보다 앞서도 회차가 걸칠 수 있어 시작만 본다. 종료일은 펼칠 때 거른다.
	fun findAllByFamilyIdAndRepeatTypeIsNotNullAndStartAtLessThanAndIsDeletedFalse(familyId: UUID, rangeEnd: LocalDateTime): List<Schedule>

	fun findAllByCreatorAccountIdAndVisibilityAndRepeatTypeIsNotNullAndStartAtLessThanAndIsDeletedFalse(
		creatorAccountId: UUID,
		visibility: Visibility,
		rangeEnd: LocalDateTime,
	): List<Schedule>

	fun findAllByRepeatTypeIsNotNullAndStartAtLessThanAndIsDeletedFalse(rangeEnd: LocalDateTime): List<Schedule>

	@Query(
		"""
		select s from Schedule s
		where s.repeatType is not null and s.startAt < :rangeEnd and s.isDeleted = false
			and s.scheduleId in (
				select a.scheduleId from ScheduleAttendee a where a.accountId = :accountId and a.isDeleted = false
			)
		""",
	)
	fun findAttendingRecurringSchedulesAndIsDeletedFalse(accountId: UUID, rangeEnd: LocalDateTime): List<Schedule>

	// 취소한 회차도 건너뛰어야 해서 삭제 상태까지 본다.
	fun findAllByRecurringScheduleIdIn(recurringScheduleIds: Collection<UUID>): List<Schedule>

	fun findByRecurringScheduleIdAndOriginalStartAt(recurringScheduleId: UUID, originalStartAt: LocalDateTime): Schedule?

	fun findAllByRecurringScheduleIdAndIsDeletedFalse(recurringScheduleId: UUID): List<Schedule>

	@Query(
		"""
		select s from Schedule s
		where s.startAt < :rangeEnd and s.endAt >= :rangeStart and s.isDeleted = false
			and s.scheduleId in (
				select a.scheduleId from ScheduleAttendee a where a.accountId = :accountId and a.isDeleted = false
			)
		""",
	)
	fun findAttendingSchedulesAndIsDeletedFalse(accountId: UUID, rangeEnd: LocalDateTime, rangeStart: LocalDateTime): List<Schedule>
}
