package com.ieum.web.exception

import com.ieum.web.response.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

// Spring MVC 표준 예외(400/404/405 등)는 부모 클래스가 상태코드를 정하고 본문만 ErrorResponse로 맞춘다.
@RestControllerAdvice
class GlobalExceptionInterceptor : ResponseEntityExceptionHandler() {

	private val log = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(IeumException::class)
	fun interceptIeumException(e: IeumException): ResponseEntity<ErrorResponse> {
		log.warn("Ieum exception. status={}, message={}", e.errorCode.status.value(), e.message)

		return ResponseEntity
			.status(e.errorCode.status)
			.body(ErrorResponse(code = e.errorCode.status.value(), message = e.errorCode.message))
	}

	override fun handleMethodArgumentNotValid(
		ex: MethodArgumentNotValidException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any> {
		val message = ex.bindingResult.fieldErrors
			.joinToString { "${it.field}: ${it.defaultMessage}" }

		log.warn("Validation failed. message={}", message)

		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ErrorResponse(code = HttpStatus.BAD_REQUEST.value(), message = message))
	}

	override fun handleExceptionInternal(
		ex: Exception,
		body: Any?,
		headers: HttpHeaders,
		statusCode: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any> {
		val message = HttpStatus.resolve(statusCode.value())?.reasonPhrase ?: "Request failed."

		log.warn("Request failed. status={}, message={}", statusCode.value(), ex.message)

		return ResponseEntity
			.status(statusCode)
			.headers(headers)
			.body(ErrorResponse(code = statusCode.value(), message = "$message."))
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
