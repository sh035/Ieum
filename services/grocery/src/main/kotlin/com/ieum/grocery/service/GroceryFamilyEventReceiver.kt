package com.ieum.grocery.service

import com.ieum.events.FamilyEventReceiver
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID

@Component
class GroceryFamilyEventReceiver(
	objectMapper: ObjectMapper,
	private val familyEventService: FamilyEventService,
) : FamilyEventReceiver(objectMapper) {

	override fun onMemberLeft(familyId: UUID, accountId: UUID, leftAt: LocalDateTime) =
		familyEventService.openPrivateItems(familyId, accountId, leftAt)

	override fun onFamilyDeleted(familyId: UUID) = familyEventService.deleteFamily(familyId)
}
