package com.ieum.account.controller.dto.response

import java.time.LocalDate

data class AccountResponse(
	val email: String,
	val nickname: String,
	val birthDate: LocalDate,
)
