package com.ieum.web.exception

import com.ieum.web.response.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionInterceptor {

	private val log = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(IeumException::class)
	fun interceptIeumException(e: IeumException): ResponseEntity<ErrorResponse> {
		log.warn("Ieum exception. status={}, message={}", e.errorCode.status.value(), e.message)

		return ResponseEntity
			.status(e.errorCode.status)
			.body(ErrorResponse(code = e.errorCode.status.value(), message = e.errorCode.message))
	}

	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun interceptValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
		val message = e.bindingResult.fieldErrors
			.joinToString { "${it.field}: ${it.defaultMessage}" }

		log.warn("Validation failed. message={}", message)

		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ErrorResponse(code = HttpStatus.BAD_REQUEST.value(), message = message))
	}

	@ExceptionHandler(Exception::class)
	fun interceptException(e: Exception): ResponseEntity<ErrorResponse> {
		log.error("Unhandled exception.", e)

		return ResponseEntity
			.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(
				ErrorResponse(
					code = HttpStatus.INTERNAL_SERVER_ERROR.value(),
					message = "Internal server error.",
				),
			)
	}
}
