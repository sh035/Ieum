package com.ieum.account.exception

import com.ieum.web.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class AccountErrorCode(
	override val status: HttpStatus,
	override val message: String,
) : ErrorCode {

	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials."),
	ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found."),
	DUPLICATE_EMAIL(HttpStatus.CONFLICT, "Email already exists."),
}
