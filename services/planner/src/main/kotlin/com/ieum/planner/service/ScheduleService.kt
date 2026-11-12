package com.ieum.planner.service

import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventPublisher
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.grpc.client.family.requireMember
import com.ieum.planner.controller.dto.request.CreateScheduleRequest
import com.ieum.planner.controller.dto.request.UpdateScheduleRequest
import com.ieum.planner.controller.dto.response.ScheduleResponse
import com.ieum.planner.domain.entity.Schedule
import com.ieum.planner.domain.entity.ScheduleAttendee
import com.ieum.planner.domain.enums.RecurrenceScope
import com.ieum.planner.domain.enums.RepeatType
import com.ieum.planner.domain.enums.Visibility
import com.ieum.planner.exception.PlannerErrorCode
import com.ieum.planner.repository.ScheduleAttendeeRepository
import com.ieum.planner.repository.ScheduleRepository
import com.ieum.planner.service.ScheduleOccurrenceExpander.Occurrence
import com.ieum.web.exception.IeumException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ScheduleService(
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
	private val familyClient: FamilyClient,
	private val notificationEventPublisher: NotificationEventPublisher,
	private val expander: ScheduleOccurrenceExpander,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	@Transactional
	fun createSchedule(accountId: String, request: CreateScheduleRequest): ScheduleResponse {
		val me = UUID.fromString(accountId)

		val familyId = when (request.visibility) {
			Visibility.FAMILY -> request.familyId ?: throw IeumException(PlannerErrorCode.INVALID_VISIBILITY_FAMILY)
			Visibility.PRIVATE -> request.familyId?.let { throw IeumException(PlannerErrorCode.INVALID_VISIBILITY_FAMILY) }
		}
		familyId?.let { familyClient.requireMember(it, me, PlannerErrorCode.FAMILY_NOT_FOUND) }

		val attendees = if (request.isAllFamily) {
			allFamily(familyId, request.attendeeAccountIds)
		} else {
			resolveAttendees(familyId, me, request.attendeeAccountIds, alreadyAttending = emptySet())
		}
		val (startAt, endAt) = period(request.isAllDay, request.startAt, request.endAt)
		checkRepeat(request.repeatType, request.repeatUntil, startAt)

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
				repeatType = request.repeatType,
				repeatUntil = request.repeatUntil,
			),
		)
		val scheduleId = requireNotNull(schedule.scheduleId)
		scheduleAttendeeRepository.saveAll(attendees.map { ScheduleAttendee(scheduleId = scheduleId, accountId = it) })

		val type = if (request.isAllFamily) NotificationEventType.FAMILY_SCHEDULE_CREATED else NotificationEventType.SCHEDULE_ATTENDEE_ADDED
		notify(type, schedule, attendees, actor = me)
		log.debug("Schedule created. scheduleId={}, accountId={}", scheduleId, accountId)

		return toResponses(listOf(Occurrence(schedule, startAt, endAt))).single()
	}

	// 기간과 겹치는 일정을 반환한다. 가족을 지정하면 그 가족 일정도 함께, 내 개인 일정은 항상 포함한다.
	@Transactional(readOnly = true)
	fun getSchedules(accountId: String, familyId: UUID?, from: LocalDate, to: LocalDate): List<ScheduleResponse> {
		if (to.isBefore(from)) {
			throw IeumException(PlannerErrorCode.INVALID_DATE_RANGE)
		}
		// 끝없는 반복을 펼치므로 캘린더와 같은 상한을 둔다.
		if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
			throw IeumException(PlannerErrorCode.CALENDAR_RANGE_TOO_LONG)
		}
		val me = UUID.fromString(accountId)
		familyId?.let { familyClient.requireMember(it, me, PlannerErrorCode.FAMILY_NOT_FOUND) }

		return findSchedules(me, familyId, from, to)
	}

	// 기간 검증과 구성원 확인은 호출하는 쪽에서 끝낸다. 반복 일정은 기간 안의 회차로 펼친다.
	fun findSchedules(me: UUID, familyId: UUID?, from: LocalDate, to: LocalDate): List<ScheduleResponse> {
		val rangeStart = from.atStartOfDay()
		val rangeEnd = to.plusDays(1).atStartOfDay()

		val familySchedules = familyId?.let {
			scheduleRepository.findAllByFamilyIdAndStartAtLessThanAndEndAtGreaterThanEqualAndIsDeletedFalse(it, rangeEnd, rangeStart) +
				scheduleRepository.findAllByFamilyIdAndRepeatTypeIsNotNullAndStartAtLessThanAndIsDeletedFalse(it, rangeEnd)
		}.orEmpty()
		val privateSchedules = scheduleRepository
			.findAllByCreatorAccountIdAndVisibilityAndStartAtLessThanAndEndAtGreaterThanEqualAndIsDeletedFalse(
				me,
				Visibility.PRIVATE,
				rangeEnd,
				rangeStart,
			) +
			scheduleRepository.findAllByCreatorAccountIdAndVisibilityAndRepeatTypeIsNotNullAndStartAtLessThanAndIsDeletedFalse(me, Visibility.PRIVATE, rangeEnd)

		return toResponses(expander.expand(familySchedules + privateSchedules, rangeStart, rangeEnd))
	}

	fun findAttendingSchedulesOn(me: UUID, familyIds: Set<UUID>, date: LocalDate): List<ScheduleResponse> {
		val rangeStart = date.atStartOfDay()
		val rangeEnd = date.plusDays(1).atStartOfDay()
		val schedules = scheduleRepository.findAttendingSchedulesAndIsDeletedFalse(me, rangeEnd, rangeStart) +
			scheduleRepository.findAttendingRecurringSchedulesAndIsDeletedFalse(me, rangeEnd)

		return toResponses(expander.expand(schedules.filter { it.familyId == null || it.familyId in familyIds }, rangeStart, rangeEnd))
	}

	// 반복 일정은 회차를 함께 받는다. 없으면 첫 회차다.
	@Transactional(readOnly = true)
	fun getSchedule(accountId: String, scheduleId: UUID, occurrenceStartAt: LocalDateTime?): ScheduleResponse {
		val schedule = findAccessibleSchedule(UUID.fromString(accountId), scheduleId)
		val occurrence = when {
			schedule.repeatType != null -> {
				val start = occurrenceStartAt ?: schedule.startAt
				if (!schedule.isOccurrence(start) || scheduleRepository.findByRecurringScheduleIdAndOriginalStartAt(scheduleId, start) != null) {
					throw IeumException(PlannerErrorCode.SCHEDULE_NOT_FOUND)
				}
				Occurrence(schedule, start, schedule.occurrenceEnd(start))
			}
			occurrenceStartAt == null || occurrenceStartAt == schedule.originalStartAt -> Occurrence(schedule, schedule.startAt, schedule.endAt)
			schedule.originalStartAt != null -> throw IeumException(PlannerErrorCode.SCHEDULE_NOT_FOUND)
			else -> throw IeumException(PlannerErrorCode.OCCURRENCE_NOT_RECURRING)
		}
		return toResponses(listOf(occurrence)).single()
	}

	@Transactional
	fun updateSchedule(
		accountId: String,
		scheduleId: UUID,
		request: UpdateScheduleRequest,
		occurrenceStartAt: LocalDateTime?,
		scope: RecurrenceScope?,
	): ScheduleResponse {
		val me = UUID.fromString(accountId)
		val (startAt, endAt) = period(request.isAllDay, request.startAt, request.endAt)

		return when (val target = resolveTarget(me, scheduleId, occurrenceStartAt, scope)) {
			is Target.Row -> {
				if (target.schedule.recurringScheduleId != null && (request.repeatType != null || request.repeatUntil != null)) {
					throw IeumException(PlannerErrorCode.REPEAT_CHANGE_NEEDS_FOLLOWING)
				}
				checkRepeat(request.repeatType, request.repeatUntil, startAt)
				updateRow(me, target.schedule, request, startAt, endAt)
			}
			is Target.One -> {
				if (request.repeatType != null || request.repeatUntil != null) {
					throw IeumException(PlannerErrorCode.REPEAT_CHANGE_NEEDS_FOLLOWING)
				}
				replaceOccurrence(me, target.master, target.startAt, request, startAt, endAt)
			}
			is Target.Following -> {
				checkRepeat(request.repeatType, request.repeatUntil, startAt)
				updateFollowing(me, target.master, target.startAt, request, startAt, endAt)
			}
		}
	}

	@Transactional
	fun deleteSchedule(accountId: String, scheduleId: UUID, occurrenceStartAt: LocalDateTime?, scope: RecurrenceScope?) {
		val me = UUID.fromString(accountId)

		when (val target = resolveTarget(me, scheduleId, occurrenceStartAt, scope)) {
			// 회차를 대신하는 행을 지우면 그 회차는 취소된 채로 남는다.
			is Target.Row -> {
				target.schedule.delete()
				scheduleRepository.save(target.schedule)
				notifyCurrentMembers(NotificationEventType.SCHEDULE_CANCELED, target.schedule, attendeeIdsOf(target.schedule), actor = me)
			}
			is Target.One -> {
				val master = target.master
				val canceled = occurrenceRow(master, target.startAt, master.title, master.memo, master.isAllDay, target.startAt, master.occurrenceEnd(target.startAt))
				canceled.delete()
				saveReplacement(canceled)
				notifyCurrentMembers(NotificationEventType.SCHEDULE_CANCELED, canceled, attendeeIdsOf(master), actor = me)
			}
			is Target.Following -> {
				endBefore(target.master, target.startAt)
				notifyCurrentMembers(NotificationEventType.SCHEDULE_CANCELED, target.master, attendeeIdsOf(target.master), actor = me, at = target.startAt)
			}
		}
	}

	// 고칠 대상이다. 일반 일정 / 회차를 대신하는 행 하나, 반복의 한 회차, 반복의 그 회차부터 끝까지.
	private sealed interface Target {
		data class Row(val schedule: Schedule) : Target

		data class One(val master: Schedule, val startAt: LocalDateTime) : Target

		data class Following(val master: Schedule, val startAt: LocalDateTime) : Target
	}

	private fun resolveTarget(me: UUID, scheduleId: UUID, occurrenceStartAt: LocalDateTime?, scope: RecurrenceScope?): Target {
		val schedule = findAccessibleSchedule(me, scheduleId)

		if (schedule.repeatType != null) {
			if (occurrenceStartAt == null || scope == null) {
				throw IeumException(PlannerErrorCode.OCCURRENCE_REQUIRED)
			}
			if (!schedule.isOccurrence(occurrenceStartAt)) {
				throw IeumException(PlannerErrorCode.SCHEDULE_NOT_FOUND)
			}
			val replaced = scheduleRepository.findByRecurringScheduleIdAndOriginalStartAt(scheduleId, occurrenceStartAt)
			return when {
				replaced?.isDeleted == true -> throw IeumException(PlannerErrorCode.SCHEDULE_NOT_FOUND)
				scope == RecurrenceScope.FOLLOWING -> Target.Following(schedule, occurrenceStartAt)
				replaced != null -> Target.Row(replaced)
				else -> Target.One(schedule, occurrenceStartAt)
			}
		}

		val originalStartAt = schedule.originalStartAt
		if (originalStartAt == null) {
			if (occurrenceStartAt != null || scope != null) {
				throw IeumException(PlannerErrorCode.OCCURRENCE_NOT_RECURRING)
			}
			return Target.Row(schedule)
		}
		if (occurrenceStartAt != null && occurrenceStartAt != originalStartAt) {
			throw IeumException(PlannerErrorCode.SCHEDULE_NOT_FOUND)
		}
		if (scope != RecurrenceScope.FOLLOWING) {
			return Target.Row(schedule)
		}
		val master = scheduleRepository.findByScheduleIdAndIsDeletedFalse(requireNotNull(schedule.recurringScheduleId))
			?: throw IeumException(PlannerErrorCode.SCHEDULE_NOT_FOUND)
		return Target.Following(master, originalStartAt)
	}

	private fun updateRow(
		me: UUID,
		schedule: Schedule,
		request: UpdateScheduleRequest,
		startAt: LocalDateTime,
		endAt: LocalDateTime,
		notifyAttendees: Boolean = true,
	): ScheduleResponse {
		val scheduleId = requireNotNull(schedule.scheduleId)
		val current = scheduleAttendeeRepository.findAllByScheduleIdInAndIsDeletedFalse(listOf(scheduleId))
		val currentIds = current.map { it.accountId }.toSet()
		val attendees = resolveAttendees(schedule.familyId, me, request.attendeeAccountIds, currentIds)
		val changed = isChanged(schedule.title, schedule.isAllDay, schedule.startAt, schedule.endAt, request, startAt, endAt)

		schedule.title = request.title
		schedule.memo = request.memo
		schedule.isAllDay = request.isAllDay
		schedule.startAt = startAt
		schedule.endAt = endAt
		schedule.repeatType = request.repeatType
		schedule.repeatUntil = request.repeatUntil
		scheduleRepository.save(schedule)
		// 종료일을 앞당기면 그 뒤의 따로 바꾼 회차가 일반 일정처럼 남지 않게 지운다.
		request.repeatUntil?.let { until -> deleteReplacements(scheduleId) { it.toLocalDate().isAfter(until) } }

		current.filter { it.accountId !in attendees }.forEach { it.delete() }
		scheduleAttendeeRepository.saveAll(current)
		scheduleAttendeeRepository.saveAll(
			(attendees - currentIds).map { ScheduleAttendee(scheduleId = scheduleId, accountId = it) },
		)
		if (notifyAttendees) {
			notifyChanges(me, schedule, currentIds, attendees, changed)
		}

		return toResponses(listOf(Occurrence(schedule, startAt, endAt))).single()
	}

	// 반복의 한 회차만 바꾼다. 원본은 그대로 두고 그 회차를 대신하는 행을 만든다.
	private fun replaceOccurrence(
		me: UUID,
		master: Schedule,
		occurrenceStartAt: LocalDateTime,
		request: UpdateScheduleRequest,
		startAt: LocalDateTime,
		endAt: LocalDateTime,
	): ScheduleResponse {
		val currentIds = attendeeIdsOf(master).toSet()
		val attendees = resolveAttendees(master.familyId, me, request.attendeeAccountIds, currentIds)
		val changed = isChanged(master.title, master.isAllDay, occurrenceStartAt, master.occurrenceEnd(occurrenceStartAt), request, startAt, endAt)

		val replacement = saveReplacement(occurrenceRow(master, occurrenceStartAt, request.title, request.memo, request.isAllDay, startAt, endAt))
		scheduleAttendeeRepository.saveAll(attendees.map { ScheduleAttendee(scheduleId = requireNotNull(replacement.scheduleId), accountId = it) })
		notifyChanges(me, replacement, currentIds, attendees, changed)

		return toResponses(listOf(Occurrence(replacement, startAt, endAt))).single()
	}

	// 그 회차 전날로 원본을 끝내고 새로 만든다. 첫 회차부터인데 규칙과 시작이 그대로면 원본만 고쳐 따로 바꾼 회차를 살린다.
	private fun updateFollowing(
		me: UUID,
		master: Schedule,
		occurrenceStartAt: LocalDateTime,
		request: UpdateScheduleRequest,
		startAt: LocalDateTime,
		endAt: LocalDateTime,
	): ScheduleResponse {
		if (occurrenceStartAt == master.startAt && startAt == master.startAt && request.repeatType == master.repeatType) {
			// 고른 첫 회차를 대신하는 행이 있으면 같은 내용으로 맞춘다. 지우면 그 회차가 취소되므로 고친다. 알림은 원본에서 한 번만 간다.
			scheduleRepository.findByRecurringScheduleIdAndOriginalStartAt(requireNotNull(master.scheduleId), occurrenceStartAt)
				?.takeUnless { it.isDeleted }
				?.let { updateRow(me, it, request.copy(repeatType = null, repeatUntil = null), startAt, endAt, notifyAttendees = false) }
			return updateRow(me, master, request, startAt, endAt)
		}

		val currentIds = attendeeIdsOf(master).toSet()
		val attendees = resolveAttendees(master.familyId, me, request.attendeeAccountIds, currentIds)
		val changed = isChanged(master.title, master.isAllDay, occurrenceStartAt, master.occurrenceEnd(occurrenceStartAt), request, startAt, endAt)
		endBefore(master, occurrenceStartAt)

		val following = scheduleRepository.save(
			Schedule(
				familyId = master.familyId,
				creatorAccountId = master.creatorAccountId,
				title = request.title,
				memo = request.memo,
				visibility = master.visibility,
				isAllDay = request.isAllDay,
				startAt = startAt,
				endAt = endAt,
				repeatType = request.repeatType,
				repeatUntil = request.repeatUntil,
			),
		)
		scheduleAttendeeRepository.saveAll(attendees.map { ScheduleAttendee(scheduleId = requireNotNull(following.scheduleId), accountId = it) })
		notifyChanges(me, following, currentIds, attendees, changed)

		return toResponses(listOf(Occurrence(following, startAt, endAt))).single()
	}

	// 그 회차부터 반복을 끝낸다. 첫 회차면 반복 전체를 지운다. 그 뒤의 따로 바꾼 회차도 함께 지운다.
	private fun endBefore(master: Schedule, occurrenceStartAt: LocalDateTime) {
		val masterId = requireNotNull(master.scheduleId)
		if (occurrenceStartAt == master.startAt) {
			master.delete()
		} else {
			master.repeatUntil = occurrenceStartAt.toLocalDate().minusDays(1)
		}
		scheduleRepository.save(master)
		deleteReplacements(masterId) { it >= occurrenceStartAt }
	}

	private fun deleteReplacements(masterId: UUID, originalStartAt: (LocalDateTime) -> Boolean) {
		scheduleRepository.findAllByRecurringScheduleIdAndIsDeletedFalse(masterId)
			.filter { originalStartAt(requireNotNull(it.originalStartAt)) }
			.forEach {
				it.delete()
				scheduleRepository.save(it)
			}
	}

	// 같은 회차를 동시에 바꾸면 unique index 에 걸린다. 트랜잭션 안이라 바로 flush 해야 여기서 잡힌다.
	private fun saveReplacement(replacement: Schedule): Schedule =
		try {
			scheduleRepository.saveAndFlush(replacement)
		} catch (e: DataIntegrityViolationException) {
			throw IeumException(PlannerErrorCode.OCCURRENCE_CHANGED_CONCURRENTLY)
		}

	// 회차를 대신하는 행은 원본의 가족 / 공개 범위 / 만든 사람을 물려받아 가족 이벤트 정리 규칙도 같다.
	private fun occurrenceRow(
		master: Schedule,
		occurrenceStartAt: LocalDateTime,
		title: String,
		memo: String?,
		isAllDay: Boolean,
		startAt: LocalDateTime,
		endAt: LocalDateTime,
	) = Schedule(
		familyId = master.familyId,
		creatorAccountId = master.creatorAccountId,
		title = title,
		memo = memo,
		visibility = master.visibility,
		isAllDay = isAllDay,
		startAt = startAt,
		endAt = endAt,
		recurringScheduleId = master.scheduleId,
		originalStartAt = occurrenceStartAt,
	)

	private fun isChanged(
		title: String,
		isAllDay: Boolean,
		startAt: LocalDateTime,
		endAt: LocalDateTime,
		request: UpdateScheduleRequest,
		newStartAt: LocalDateTime,
		newEndAt: LocalDateTime,
	) = title != request.title || isAllDay != request.isAllDay || startAt != newStartAt || endAt != newEndAt

	// 한 사람이 알림을 두 개 받지 않게 새 참석자 / 남은 참석자 / 빠진 참석자로 나눈다.
	private fun notifyChanges(me: UUID, schedule: Schedule, currentIds: Set<UUID>, attendees: List<UUID>, changed: Boolean) {
		notify(NotificationEventType.SCHEDULE_ATTENDEE_ADDED, schedule, attendees - currentIds, actor = me)
		if (changed) {
			notifyCurrentMembers(NotificationEventType.SCHEDULE_UPDATED, schedule, attendees.filter { it in currentIds }, actor = me)
		}
		notifyCurrentMembers(NotificationEventType.SCHEDULE_CANCELED, schedule, currentIds - attendees.toSet(), actor = me)
	}

	private fun attendeeIdsOf(schedule: Schedule): List<UUID> =
		scheduleAttendeeRepository.findAllByScheduleIdInAndIsDeletedFalse(listOf(requireNotNull(schedule.scheduleId))).map { it.accountId }

	// 마지막 회차 날짜는 반복할 때만, 첫 회차보다 앞설 수 없다.
	private fun checkRepeat(repeatType: RepeatType?, repeatUntil: LocalDate?, startAt: LocalDateTime) {
		if (repeatUntil != null && (repeatType == null || repeatUntil.isBefore(startAt.toLocalDate()))) {
			throw IeumException(PlannerErrorCode.INVALID_REPEAT_UNTIL)
		}
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

	// 요청 시점 구성원으로 펼쳐 저장한다. 일정에 "전체" 표시는 남기지 않는다.
	private fun allFamily(familyId: UUID?, attendeeAccountIds: List<UUID>): List<UUID> {
		if (familyId == null || attendeeAccountIds.isNotEmpty()) {
			throw IeumException(PlannerErrorCode.INVALID_ALL_FAMILY)
		}
		val family = familyClient.getFamily(familyId) ?: throw IeumException(PlannerErrorCode.FAMILY_NOT_FOUND)
		return family.members.keys.toList()
	}

	// 기존 참석자는 탈퇴 이벤트가 처리되기 전이면 이미 가족을 떠났을 수 있어 현재 구성원만 남긴다.
	// 알림 때문에 일정 저장이 취소되면 안 되므로 조회 실패는 알림만 포기한다.
	private fun notifyCurrentMembers(
		type: NotificationEventType,
		schedule: Schedule,
		recipients: Collection<UUID>,
		actor: UUID,
		at: LocalDateTime = schedule.startAt,
	) {
		val others = recipients.filter { it != actor }
		val familyId = schedule.familyId ?: return notify(type, schedule, others, actor, at)
		val members = try {
			familyClient.getRoles(familyId, others).keys
		} catch (e: IeumException) {
			log.warn("Schedule notification skipped. scheduleId={}, message={}", schedule.scheduleId, e.message)
			return
		}
		notify(type, schedule, others.filter { it in members }, actor, at)
	}

	// 받는 사람에서 행동한 본인은 발행기가 거른다.
	private fun notify(
		type: NotificationEventType,
		schedule: Schedule,
		recipients: Collection<UUID>,
		actor: UUID,
		at: LocalDateTime = schedule.startAt,
	) {
		notificationEventPublisher.publish(
			type,
			NotificationEventData(
				recipientAccountIds = recipients.toList(),
				familyId = schedule.familyId,
				targetType = NotificationTargetType.SCHEDULE,
				targetId = schedule.scheduleId,
				actorAccountId = actor,
				subject = schedule.title,
				at = at,
			),
		)
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

	// 참석자는 일정 수와 상관없이 한 번에 조회한다. 반복 회차는 원본의 참석자를 쓴다.
	private fun toResponses(occurrences: List<Occurrence>): List<ScheduleResponse> {
		if (occurrences.isEmpty()) return emptyList()

		val attendees = scheduleAttendeeRepository
			.findAllByScheduleIdInAndIsDeletedFalse(occurrences.map { requireNotNull(it.schedule.scheduleId) }.distinct())
			.groupBy({ it.scheduleId }, { it.accountId })

		return occurrences.map { it.toResponse(attendees[it.schedule.scheduleId].orEmpty()) }
	}

	private fun Occurrence.toResponse(attendeeAccountIds: List<UUID>) = ScheduleResponse(
		scheduleId = requireNotNull(schedule.scheduleId),
		familyId = schedule.familyId,
		creatorAccountId = schedule.creatorAccountId,
		title = schedule.title,
		memo = schedule.memo,
		visibility = schedule.visibility,
		isAllDay = schedule.isAllDay,
		startAt = startAt,
		endAt = endAt,
		attendeeAccountIds = attendeeAccountIds,
		repeatType = schedule.repeatType,
		repeatUntil = schedule.repeatUntil,
		recurringScheduleId = schedule.recurringScheduleId,
		occurrenceStartAt = if (schedule.repeatType != null) startAt else schedule.originalStartAt,
	)

	companion object {
		// 월 보기 6주(42칸)까지다. 캘린더도 같은 값을 쓴다.
		const val MAX_RANGE_DAYS = 42L
	}
}
