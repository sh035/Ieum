package com.ieum.grocery.exception

import com.ieum.web.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class GroceryErrorCode(
	override val status: HttpStatus,
	override val message: String,
) : ErrorCode {

	FAMILY_NOT_FOUND(HttpStatus.NOT_FOUND, "Family not found."),
	STORAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Storage not found."),
	INVALID_STORAGE_NAME(HttpStatus.BAD_REQUEST, "Storage name must be 1 to 50 characters."),
	STORAGE_NAME_DUPLICATED(HttpStatus.CONFLICT, "Storage name already exists in the family."),
	STORAGE_NOT_EMPTY(HttpStatus.CONFLICT, "Storage still has items."),
	FAMILY_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Family service unavailable."),
}
