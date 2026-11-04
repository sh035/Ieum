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
	INVALID_ROLE_CHANGE(HttpStatus.BAD_REQUEST, "Only MEMBER and MANAGER can be switched, and the owner role cannot change."),
	CANNOT_REMOVE_OWNER(HttpStatus.BAD_REQUEST, "The owner cannot be removed."),
	INVALID_OWNERSHIP_TRANSFER(HttpStatus.BAD_REQUEST, "Ownership cannot be transferred to yourself."),
	OWNERSHIP_TRANSFER_REQUIRED(HttpStatus.CONFLICT, "Transfer ownership before leaving the family."),
	INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Invitation not found."),
	INVITEE_NOT_FOUND(HttpStatus.NOT_FOUND, "Invitee not found."),
	INVITATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "An invitation for this account is already pending."),
	ALREADY_FAMILY_MEMBER(HttpStatus.CONFLICT, "Already a family member."),
}
