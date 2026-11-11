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
	CURRENT_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "Current password is required."),
	WRONG_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "Current password is incorrect."),
	REJOIN_TOO_SOON(HttpStatus.CONFLICT, "You can sign up again with this email 3 days after withdrawal."),
	OWNERSHIP_TRANSFER_REQUIRED(HttpStatus.CONFLICT, "Transfer ownership of your families before withdrawal."),
	SOLO_FAMILY_DELETION_NOT_CONFIRMED(HttpStatus.CONFLICT, "Confirm deleting the families you are alone in."),
}
