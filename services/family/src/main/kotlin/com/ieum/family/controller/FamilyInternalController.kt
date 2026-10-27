package com.ieum.family.controller

import com.ieum.family.service.FamilyMemberService
import com.ieum.web.response.BaseResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 서비스 간 호출 전용. Gateway에 노출하지 않는다.
@RestController
@RequestMapping("/internal/family")
class FamilyInternalController(
	private val familyMemberService: FamilyMemberService,
) {

	@GetMapping("/{familyId}/member/{accountId}/role")
	fun getMemberRole(
		@PathVariable familyId: UUID,
		@PathVariable accountId: UUID,
	): BaseResponseEntity {
		return BaseResponseEntity.single(familyMemberService.getRole(familyId, accountId))
	}
}
