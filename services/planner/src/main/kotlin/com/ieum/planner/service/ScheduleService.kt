package com.ieum.planner.service

import com.ieum.grpc.client.family.FamilyClient
import com.ieum.planner.controller.dto.request.CreateScheduleRequest
import com.ieum.planner.controller.dto.request.UpdateScheduleRequest
import com.ieum.planner.controller.dto.response.ScheduleResponse
import com.ieum.planner.domain.entity.Schedule
import com.ieum.planner.domain.entity.ScheduleAttendee
import com.ieum.planner.domain.enums.Visibility
import com.ieum.planner.exception.PlannerErrorCode
import com.ieum.planner.repository.ScheduleAttendeeRepository
import com.ieum.planner.repository.ScheduleRepository
import com.ieum.web.exception.IeumException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class ScheduleService(
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
	private val familyClient: FamilyClient,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	@Transactional
	fun createSchedule(accountId: String, request: CreateScheduleRequest): ScheduleResponse {
		val me = UUID.fromString(accountId)

		val familyId = when (request.visibility) {
			Visibility.FAMILY -> request.familyId ?: throw IeumException(PlannerErrorCode.INVALID_VISIBILITY_FAMILY)
			Visibility.PRIVATE -> request.familyId?.let { throw IeumException(PlannerErrorCode.INVALID_VISIBILITY_FAMILY) }
		}
		familyId?.let { checkMember(it, me) }

		val attendees = resolveAttendees(familyId, me, request.attendeeAccountIds, alreadyAttending = emptySet())
		val (startAt, endAt) = period(request.isAllDay, request.startAt, request.endAt)

		val schedule = scheduleRepository.save(
			Schedule(
				familyId = familyId,
				creatorAccountId = me,
				title = request.title,
				memo = request.memo,
				visibility = request.visibility,
				isAllDay = request.isAllDay,
				startAt = startAt,
				endAt = endAt,
			),
		)
		val scheduleId = requireNotNull(schedule.scheduleId)
		scheduleAttendeeRepository.saveAll(attendees.map { ScheduleAttendee(scheduleId = scheduleId, accountId = it) })

		log.debug("Schedule created. scheduleId={}, accountId={}", scheduleId, accountId)

		return schedule.toResponse(attendees)
	}

	// 기간과 겹치는 일정을 반환한다. 가족을 지정하면 그 가족 일정도 함께, 내 개인 일정은 항상 포함한다.
	@Transactional(readOnly = true)
	fun getSchedules(accountId: String, familyId: UUID?, from: LocalDate, to: LocalDate): List<ScheduleResponse> {
		if (to.isBefore(from)) {
			throw IeumException(PlannerErrorCode.INVALID_DATE_RANGE)
		}
		val me = UUID.fromString(accountId)
		familyId?.let { checkMember(it, me) }

		return findSchedules(me, familyId, from, to)
	}

	// 기간 검증과 구성원 확인은 호출하는 쪽에서 끝낸다.
	fun findSchedules(me: UUID, familyId: UUID?, from: LocalDate, to: LocalDate): List<ScheduleResponse> {
		val rangeStart = from.atStartOfDay()
		val rangeEnd = to.plusDays(1).atStartOfDay()

		val familySchedules = familyId?.let {
			scheduleRepository.findAllByFamilyIdAndStartAtLessThanAndEndAtGreaterThanEqualAndIsDeletedFalse(it, rangeEnd, rangeStart)
		}.orEmpty()
		val privateSchedules = scheduleRepository
			.findAllByCreatorAccountIdAndVisibilityAndStartAtLessThanAndEndAtGreaterThanEqualAndIsDeletedFalse(
				me,
				Visibility.PRIVATE,
				rangeEnd,
				rangeStart,
			)

		return toResponses((familySchedules + privateSchedules).sortedWith(compareBy({ it.startAt }, { it.createdAt })))
	}

	@Transactional(readOnly = true)
	fun getSchedule(accountId: String, scheduleId: UUID): ScheduleResponse =
		toResponses(listOf(findAccessibleSchedule(UUID.fromString(accountId), scheduleId))).single()

	@Transactional
	fun updateSchedule(accountId: String, scheduleId: UUID, request: UpdateScheduleRequest): ScheduleResponse {
		val me = UUID.fromString(accountId)
		val schedule = findAccessibleSchedule(me, scheduleId)
		val current = scheduleAttendeeRepository.findAllByScheduleIdInAndIsDeletedFalse(listOf(scheduleId))

		val attendees = resolveAttendees(schedule.familyId, me, request.attendeeAccountIds, current.map { it.accountId }.toSet())
		val (startAt, endAt) = period(request.isAllDay, request.startAt, request.endAt)

		schedule.title = request.title
		schedule.memo = request.memo
		schedule.isAllDay = request.isAllDay
		schedule.startAt = startAt
		schedule.endAt = endAt
		scheduleRepository.save(schedule)

		current.filter { it.accountId !in attendees }.forEach { it.delete() }
		scheduleAttendeeRepository.saveAll(current)
		scheduleAttendeeRepository.saveAll(
			(attendees - current.map { it.accountId }.toSet()).map { ScheduleAttendee(scheduleId = scheduleId, accountId = it) },
		)

		return schedule.toResponse(attendees)
	}

	fun deleteSchedule(accountId: String, scheduleId: UUID) {
		val schedule = findAccessibleSchedule(UUID.fromString(accountId), scheduleId)
		schedule.delete()
		scheduleRepository.save(schedule)
	}

	// 볼 수 없는 일정은 존재 자체를 숨긴다.
	private fun findAccessibleSchedule(me: UUID, scheduleId: UUID): Schedule {
		val schedule = scheduleRepository.findByScheduleIdAndIsDeletedFalse(scheduleId)
			?: throw IeumException(PlannerErrorCode.SCHEDULE_NOT_FOUND)

		val accessible = when (schedule.visibility) {
			Visibility.FAMILY -> familyClient.isMember(requireNotNull(schedule.familyId), me)
			Visibility.PRIVATE -> schedule.creatorAccountId == me
		}
		if (!accessible) {
			throw IeumException(PlannerErrorCode.SCHEDULE_NOT_FOUND)
		}

		return schedule
	}

	// 개인 일정의 참석자는 본인뿐이다. 가족 일정은 새로 추가되는 참석자만 구성원인지 확인한다.
	private fun resolveAttendees(
		familyId: UUID?,
		me: UUID,
		attendeeAccountIds: List<UUID>,
		alreadyAttending: Set<UUID>,
	): List<UUID> {
		val attendees = attendeeAccountIds.distinct()

		if (familyId == null) {
			if (attendees.any { it != me }) {
				throw IeumException(PlannerErrorCode.INVALID_ATTENDEE)
			}
			return listOf(me)
		}

		if (attendees.isEmpty()) {
			throw IeumException(PlannerErrorCode.ATTENDEE_REQUIRED)
		}
		val added = attendees.filter { it != me && it !in alreadyAttending }
		if (!familyClient.getRoles(familyId, added).keys.containsAll(added)) {
			throw IeumException(PlannerErrorCode.INVALID_ATTENDEE)
		}
		return attendees
	}

	// 종일 일정은 시간을 버리고 날짜만 남긴다.
	private fun period(isAllDay: Boolean, startAt: LocalDateTime, endAt: LocalDateTime): Pair<LocalDateTime, LocalDateTime> {
		val period = if (isAllDay) {
			startAt.toLocalDate().atStartOfDay() to endAt.toLocalDate().atStartOfDay()
		} else {
			startAt to endAt
		}
		if (period.second.isBefore(period.first)) {
			throw IeumException(PlannerErrorCode.INVALID_SCHEDULE_PERIOD)
		}
		return period
	}

	private fun checkMember(familyId: UUID, accountId: UUID) {
		if (!familyClient.isMember(familyId, accountId)) {
			throw IeumException(PlannerErrorCode.FAMILY_NOT_FOUND)
		}
	}

	// 참석자는 일정 수와 상관없이 한 번에 조회한다.
	private fun toResponses(schedules: List<Schedule>): List<ScheduleResponse> {
		if (schedules.isEmpty()) return emptyList()

		val attendees = scheduleAttendeeRepository
			.findAllByScheduleIdInAndIsDeletedFalse(schedules.map { requireNotNull(it.scheduleId) })
			.groupBy({ it.scheduleId }, { it.accountId })

		return schedules.map { it.toResponse(attendees[it.scheduleId].orEmpty()) }
	}

	private fun Schedule.toResponse(attendeeAccountIds: List<UUID>) = ScheduleResponse(
		scheduleId = requireNotNull(scheduleId),
		familyId = familyId,
		creatorAccountId = creatorAccountId,
		title = title,
		memo = memo,
		visibility = visibility,
		isAllDay = isAllDay,
		startAt = startAt,
		endAt = endAt,
		attendeeAccountIds = attendeeAccountIds,
	)
}
