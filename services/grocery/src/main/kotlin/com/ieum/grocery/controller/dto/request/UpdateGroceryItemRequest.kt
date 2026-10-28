package com.ieum.grocery.controller.dto.request

import com.ieum.grocery.domain.enums.GroceryCategory
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

// 수정 가능한 필드를 모두 보낸다. null 은 값을 지운다.
data class UpdateGroceryItemRequest(
	val storageId: UUID,
	@field:NotBlank
	@field:Size(max = 100)
	val name: String,
	val category: GroceryCategory,
	@field:Min(1)
	@field:Max(9999)
	val quantity: Int,
	@field:PositiveOrZero
	val purchasePrice: Int?,
	val purchaseDate: LocalDate?,
	val expiryDate: LocalDate?,
)
