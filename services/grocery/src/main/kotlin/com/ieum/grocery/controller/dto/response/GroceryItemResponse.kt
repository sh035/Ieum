package com.ieum.grocery.controller.dto.response

import com.ieum.grocery.domain.enums.GroceryCategory
import com.ieum.grocery.domain.enums.Visibility
import java.time.LocalDate
import java.util.UUID

data class GroceryItemResponse(
	val groceryItemId: UUID,
	val storageId: UUID,
	val familyId: UUID,
	val creatorAccountId: UUID,
	val name: String,
	val category: GroceryCategory,
	val visibility: Visibility,
	val quantity: Int,
	val purchasePrice: Int?,
	val purchaseDate: LocalDate?,
	val expiryDate: LocalDate?,
)
