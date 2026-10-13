package com.ieum.web.response

import org.springframework.http.HttpStatus
import java.time.LocalDateTime

abstract class BaseResponse(
	status: HttpStatus,
) {

	val code: Int = status.value()
	val timestamp: LocalDateTime = LocalDateTime.now()
}
