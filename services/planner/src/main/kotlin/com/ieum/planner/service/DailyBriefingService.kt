package com.ieum.planner.service

import com.ieum.grpc.client.family.FamilyClient
import com.ieum.planner.controller.dto.response.DailyBriefingResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class DailyBriefingService(
	private val todoService: TodoService,
	private val scheduleService: ScheduleService,
	private val familyClient: FamilyClient,
) {

	// 떠난 가족의 항목은 삭제 이벤트가 처리되기 전에도 보이면 안 되므로 지금 속한 가족만 남긴다.
	@Transactional(readOnly = true)
	fun getDailyBriefing(accountId: String): DailyBriefingResponse {
		val me = UUID.fromString(accountId)
		val today = LocalDate.now()
		val familyIds = familyClient.getFamilyIds(me).toSet()
		val (todos, overdueTodos) = todoService.findMyOpenTodosDueUntil(me, familyIds, today).partition { it.dueDate == today }

		return DailyBriefingResponse(
			todos = todos,
			overdueTodos = overdueTodos,
			schedules = scheduleService.findAttendingSchedulesOn(me, familyIds, today),
		)
	}
}
