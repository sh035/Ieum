package com.ieum.grpc.client

import com.ieum.web.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class GrpcClientErrorCode(
	override val status: HttpStatus,
	override val message: String,
) : ErrorCode {
	FAMILY_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Family service unavailable."),
	ACCOUNT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Account service unavailable."),
}
