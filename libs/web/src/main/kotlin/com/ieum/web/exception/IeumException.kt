package com.ieum.web.exception

import org.springframework.http.HttpStatus

abstract class IeumException(
	val status: HttpStatus,
	override val message: String,
) : RuntimeException(message)
