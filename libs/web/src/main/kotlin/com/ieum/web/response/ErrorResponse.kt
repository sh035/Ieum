package com.ieum.web.response

import java.time.LocalDateTime

data class ErrorResponse(
	val code: Int,
	val message: String,
	val timestamp: LocalDateTime = LocalDateTime.now(),
)
