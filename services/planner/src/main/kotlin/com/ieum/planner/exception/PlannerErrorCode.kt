package com.ieum.planner.exception

import com.ieum.web.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class PlannerErrorCode(
	override val status: HttpStatus,
	override val message: String,
) : ErrorCode {

	TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "Todo not found."),
	CHECKLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Checklist item not found."),
	FAMILY_NOT_FOUND(HttpStatus.NOT_FOUND, "Family not found."),
	INVALID_VISIBILITY_FAMILY(HttpStatus.BAD_REQUEST, "Family item needs familyId and private item must not have one."),
	VISIBILITY_REQUIRED(HttpStatus.BAD_REQUEST, "Top-level todo needs visibility."),
	SUBTASK_VISIBILITY_INHERITED(HttpStatus.BAD_REQUEST, "Subtask inherits visibility and familyId from its parent."),
	SUBTASK_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "Subtask can be nested only two levels deep."),
	ASSIGNEE_REQUIRED(HttpStatus.BAD_REQUEST, "Family todo needs an assignee."),
	INVALID_ASSIGNEE(HttpStatus.BAD_REQUEST, "Assignee must be a family member, or yourself for a private todo."),
	DUE_TIME_WITHOUT_DATE(HttpStatus.BAD_REQUEST, "Due time needs a due date."),
	SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "Schedule not found."),
	ATTENDEE_REQUIRED(HttpStatus.BAD_REQUEST, "Family schedule needs at least one attendee."),
	INVALID_ATTENDEE(HttpStatus.BAD_REQUEST, "Attendees must be family members, or only yourself for a private schedule."),
	INVALID_ALL_FAMILY(HttpStatus.BAD_REQUEST, "All-family attendees need a family schedule without an attendee list."),
	INVALID_SCHEDULE_PERIOD(HttpStatus.BAD_REQUEST, "Schedule must not end before it starts."),
	INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "Range must not end before it starts."),
	CALENDAR_RANGE_TOO_LONG(HttpStatus.BAD_REQUEST, "Calendar range must be 42 days or less."),
}
