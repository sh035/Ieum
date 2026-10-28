package com.ieum.planner.exception

import com.ieum.web.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class PlannerErrorCode(
	override val status: HttpStatus,
	override val message: String,
) : ErrorCode {

	TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "Todo not found."),
	FAMILY_NOT_FOUND(HttpStatus.NOT_FOUND, "Family not found."),
	INVALID_TODO_FAMILY(HttpStatus.BAD_REQUEST, "Family todo needs familyId and private todo must not have one."),
	ASSIGNEE_REQUIRED(HttpStatus.BAD_REQUEST, "Family todo needs an assignee."),
	INVALID_ASSIGNEE(HttpStatus.BAD_REQUEST, "Assignee must be a family member, or yourself for a private todo."),
	DUE_TIME_WITHOUT_DATE(HttpStatus.BAD_REQUEST, "Due time needs a due date."),
	FAMILY_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Family service unavailable."),
}
