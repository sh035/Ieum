package com.ieum.expense.service

import com.ieum.events.FamilyEventReceiver
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID

@Component
class ExpenseFamilyEventReceiver(
	objectMapper: ObjectMapper,
	private val familyEventService: FamilyEventService,
) : FamilyEventReceiver(objectMapper) {

	// 지출은 이미 쓴 돈의 기록이라 구성원이 떠나도 가족 기록으로 남긴다.
	override fun onMemberLeft(familyId: UUID, accountId: UUID, leftAt: LocalDateTime) = Unit

	override fun onFamilyDeleted(familyId: UUID) = familyEventService.deleteFamily(familyId)
}
