package com.ieum.grocery.controller

import com.ieum.grocery.controller.dto.request.CreateStorageRequest
import com.ieum.grocery.controller.dto.request.UpdateStorageRequest
import com.ieum.grocery.service.StorageService
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
@RequestMapping("/api/v1/grocery/storage")
class StorageController(
	private val storageService: StorageService,
) {

	@PostMapping
	fun createStorages(
		@AuthenticationPrincipal jwt: Jwt,
		@Valid @RequestBody request: CreateStorageRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.created(storageService.createStorages(requireNotNull(jwt.subject), request))
	}

	@GetMapping
	fun getStorages(
		@AuthenticationPrincipal jwt: Jwt,
		@RequestParam familyId: UUID,
	): BaseResponseEntity {
		return BaseResponseEntity.single(storageService.getStorages(requireNotNull(jwt.subject), familyId))
	}

	@PatchMapping("/{storageId}")
	fun updateStorage(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable storageId: UUID,
		@Valid @RequestBody request: UpdateStorageRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.single(storageService.updateStorage(requireNotNull(jwt.subject), storageId, request))
	}

	@DeleteMapping("/{storageId}")
	fun deleteStorage(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable storageId: UUID,
	): BaseResponseEntity {
		storageService.deleteStorage(requireNotNull(jwt.subject), storageId)
		return BaseResponseEntity.emptyNoContent()
	}
}
