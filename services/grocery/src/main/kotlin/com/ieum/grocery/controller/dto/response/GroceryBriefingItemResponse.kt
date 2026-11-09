package com.ieum.grocery.controller.dto.response

import com.ieum.grocery.domain.enums.ExpiryStatus
import java.time.LocalDate
import java.util.UUID

data class GroceryBriefingItemResponse(
	val groceryItemId: UUID,
	val familyId: UUID,
	val name: String,
	val quantity: Int,
	val expiryDate: LocalDate,
	val expiryStatus: ExpiryStatus,
)
