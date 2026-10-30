package com.ieum.grpc.server

import com.ieum.web.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class GrpcServerErrorCode(
	override val status: HttpStatus,
	override val message: String,
) : ErrorCode {
	INVALID_UUID(HttpStatus.BAD_REQUEST, "Invalid uuid."),
}
