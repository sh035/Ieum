package com.ieum.grocery.controller.dto.request

import com.ieum.grocery.domain.enums.GroceryCategory
import com.ieum.grocery.domain.enums.Visibility
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

data class CreateGroceryItemRequest(
	val storageId: UUID,
	@field:NotBlank
	@field:Size(max = 100)
	val name: String,
	val category: GroceryCategory,
	val visibility: Visibility,
	@field:Min(1)
	@field:Max(9999)
	val quantity: Int = 1,
	@field:PositiveOrZero
	val purchasePrice: Int?,
	val purchaseDate: LocalDate?,
	val expiryDate: LocalDate?,
)
