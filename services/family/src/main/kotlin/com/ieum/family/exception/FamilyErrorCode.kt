package com.ieum.family.exception

import com.ieum.web.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class FamilyErrorCode(
	override val status: HttpStatus,
	override val message: String,
) : ErrorCode {

	FAMILY_NOT_FOUND(HttpStatus.NOT_FOUND, "Family not found."),
	FAMILY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Family access denied."),
	FAMILY_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Family member not found."),
	INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Invitation not found."),
	ALREADY_FAMILY_MEMBER(HttpStatus.CONFLICT, "Already a family member."),
}
