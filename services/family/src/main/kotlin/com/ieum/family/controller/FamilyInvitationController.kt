package com.ieum.family.controller

import com.ieum.family.controller.dto.request.CreateFamilyInvitationRequest
import com.ieum.family.service.FamilyInvitationService
import com.ieum.web.response.BaseResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class FamilyInvitationController(
	private val familyInvitationService: FamilyInvitationService,
) {

	@GetMapping("/family/{familyId}/invitation/candidate")
	fun findCandidate(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable familyId: UUID,
		@RequestParam email: String,
	): BaseResponseEntity {
		return BaseResponseEntity.single(familyInvitationService.findCandidate(requireNotNull(jwt.subject), familyId, email))
	}

	@PostMapping("/family/{familyId}/invitation")
	fun createInvitation(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable familyId: UUID,
		@RequestBody request: CreateFamilyInvitationRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.created(
			familyInvitationService.createInvitation(requireNotNull(jwt.subject), familyId, request.inviteeAccountId),
		)
	}

	@GetMapping("/family/{familyId}/invitation")
	fun getInvitations(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable familyId: UUID,
	): BaseResponseEntity {
		return BaseResponseEntity.single(familyInvitationService.getInvitations(requireNotNull(jwt.subject), familyId))
	}

	@DeleteMapping("/family/{familyId}/invitation/{familyInvitationId}")
	fun cancelInvitation(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable familyId: UUID,
		@PathVariable familyInvitationId: UUID,
	): BaseResponseEntity {
		familyInvitationService.cancelInvitation(requireNotNull(jwt.subject), familyId, familyInvitationId)
		return BaseResponseEntity.emptyNoContent()
	}

	@GetMapping("/family-invitation")
	fun getReceivedInvitations(
		@AuthenticationPrincipal jwt: Jwt,
	): BaseResponseEntity {
		return BaseResponseEntity.single(familyInvitationService.getReceivedInvitations(requireNotNull(jwt.subject)))
	}

	@PostMapping("/family-invitation/{familyInvitationId}/accept")
	fun acceptInvitation(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable familyInvitationId: UUID,
	): BaseResponseEntity {
		return BaseResponseEntity.created(familyInvitationService.acceptInvitation(requireNotNull(jwt.subject), familyInvitationId))
	}

	@PostMapping("/family-invitation/{familyInvitationId}/decline")
	fun declineInvitation(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable familyInvitationId: UUID,
	): BaseResponseEntity {
		familyInvitationService.declineInvitation(requireNotNull(jwt.subject), familyInvitationId)
		return BaseResponseEntity.emptyNoContent()
	}
}
