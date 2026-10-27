package com.ieum.account.controller.dto.response

import java.util.UUID

data class AccountProfileResponse(
	val accountId: UUID,
	val nickname: String,
)
