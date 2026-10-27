package com.ieum.family.controller

import com.ieum.family.service.FamilyInvitationService
import com.ieum.web.response.BaseResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class FamilyInvitationController(
	private val familyInvitationService: FamilyInvitationService,
) {

	@PostMapping("/family/{familyId}/invitation")
	fun createInvitation(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable familyId: UUID,
	): BaseResponseEntity {
		return BaseResponseEntity.created(familyInvitationService.createInvitation(requireNotNull(jwt.subject), familyId))
	}

	@GetMapping("/family/{familyId}/invitation")
	fun getInvitation(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable familyId: UUID,
	): BaseResponseEntity {
		return BaseResponseEntity.single(familyInvitationService.getInvitation(requireNotNull(jwt.subject), familyId))
	}

	@DeleteMapping("/family/{familyId}/invitation")
	fun cancelInvitation(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable familyId: UUID,
	): BaseResponseEntity {
		familyInvitationService.cancelInvitation(requireNotNull(jwt.subject), familyId)
		return BaseResponseEntity.emptyNoContent()
	}

	@GetMapping("/family-invitation/{code}")
	fun verifyInvitation(
		@PathVariable code: String,
	): BaseResponseEntity {
		return BaseResponseEntity.single(familyInvitationService.verifyInvitation(code))
	}

	@PostMapping("/family-invitation/{code}/accept")
	fun acceptInvitation(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable code: String,
	): BaseResponseEntity {
		return BaseResponseEntity.created(familyInvitationService.acceptInvitation(requireNotNull(jwt.subject), code))
	}
}
