package com.ieum.family.controller

import com.ieum.family.controller.dto.request.UpdateFamilyMemberRoleRequest
import com.ieum.family.service.FamilyMemberService
import com.ieum.web.response.BaseResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/family/{familyId}/member")
class FamilyMemberController(
	private val familyMemberService: FamilyMemberService,
) {

	@GetMapping
	fun getMembers(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable familyId: UUID,
	): BaseResponseEntity {
		return BaseResponseEntity.single(familyMemberService.getMembers(requireNotNull(jwt.subject), familyId))
	}

	@PatchMapping("/{familyMemberId}/role")
	fun updateRole(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable familyId: UUID,
		@PathVariable familyMemberId: UUID,
		@RequestBody request: UpdateFamilyMemberRoleRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.single(
			familyMemberService.updateRole(requireNotNull(jwt.subject), familyId, familyMemberId, request),
		)
	}

	@DeleteMapping("/{familyMemberId}")
	fun removeMember(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable familyId: UUID,
		@PathVariable familyMemberId: UUID,
	): BaseResponseEntity {
		familyMemberService.removeMember(requireNotNull(jwt.subject), familyId, familyMemberId)
		return BaseResponseEntity.emptyNoContent()
	}

	@PostMapping("/{familyMemberId}/transfer-ownership")
	fun transferOwnership(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable familyId: UUID,
		@PathVariable familyMemberId: UUID,
	): BaseResponseEntity {
		familyMemberService.transferOwnership(requireNotNull(jwt.subject), familyId, familyMemberId)
		return BaseResponseEntity.emptyNoContent()
	}
}
