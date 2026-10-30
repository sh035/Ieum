package com.ieum.notification.exception

import com.ieum.web.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class NotificationErrorCode(
	override val status: HttpStatus,
	override val message: String,
) : ErrorCode {

	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification not found."),
	FAMILY_NOT_FOUND(HttpStatus.NOT_FOUND, "Family not found."),
	FAMILY_ID_REQUIRED(HttpStatus.BAD_REQUEST, "familyId is required for the family tab."),
}
