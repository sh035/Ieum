package com.ieum.family.controller.dto.request

import com.ieum.family.domain.enums.FamilyRole

data class UpdateFamilyMemberRoleRequest(
	val role: FamilyRole,
)
