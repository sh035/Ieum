package com.ieum.grocery.controller

import com.ieum.grocery.controller.dto.request.CreateGroceryItemRequest
import com.ieum.grocery.controller.dto.request.UpdateGroceryItemRequest
import com.ieum.grocery.service.GroceryItemService
import com.ieum.web.response.BaseResponseEntity
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/grocery/item")
class GroceryItemController(
	private val groceryItemService: GroceryItemService,
) {

	@PostMapping
	fun createItem(
		@AuthenticationPrincipal jwt: Jwt,
		@Valid @RequestBody request: CreateGroceryItemRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.created(groceryItemService.createItem(requireNotNull(jwt.subject), request))
	}

	@GetMapping
	fun getItems(
		@AuthenticationPrincipal jwt: Jwt,
		@RequestParam familyId: UUID,
		@RequestParam(required = false) storageId: UUID?,
	): BaseResponseEntity {
		return BaseResponseEntity.single(groceryItemService.getItems(requireNotNull(jwt.subject), familyId, storageId))
	}

	@GetMapping("/{groceryItemId}")
	fun getItem(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable groceryItemId: UUID,
	): BaseResponseEntity {
		return BaseResponseEntity.single(groceryItemService.getItem(requireNotNull(jwt.subject), groceryItemId))
	}

	@PatchMapping("/{groceryItemId}")
	fun updateItem(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable groceryItemId: UUID,
		@Valid @RequestBody request: UpdateGroceryItemRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.single(groceryItemService.updateItem(requireNotNull(jwt.subject), groceryItemId, request))
	}

	@DeleteMapping("/{groceryItemId}")
	fun deleteItem(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable groceryItemId: UUID,
	): BaseResponseEntity {
		groceryItemService.deleteItem(requireNotNull(jwt.subject), groceryItemId)
		return BaseResponseEntity.emptyNoContent()
	}
}
