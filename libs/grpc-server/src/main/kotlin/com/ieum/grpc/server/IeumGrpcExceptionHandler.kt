package com.ieum.grpc.server

import com.ieum.web.exception.IeumException
import io.grpc.Status
import io.grpc.StatusException
import org.springframework.grpc.server.exception.GrpcExceptionHandler
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

// 서비스가 던진 IeumException 의 HTTP 상태를 gRPC 상태로 바꾼다. REST 의 GlobalExceptionInterceptor 와 같은 역할이다.
// IllegalArgumentException 은 서버 버그도 던지므로 INVALID_ARGUMENT 로 바꾸지 않고 기본 처리(INTERNAL)에 맡긴다.
@Component
class IeumGrpcExceptionHandler : GrpcExceptionHandler {

	override fun handleException(exception: Throwable): StatusException? {
		val errorCode = (exception as? IeumException)?.errorCode ?: return null
		val status = when (errorCode.status) {
			HttpStatus.NOT_FOUND -> Status.NOT_FOUND
			HttpStatus.BAD_REQUEST -> Status.INVALID_ARGUMENT
			else -> return null
		}

		return status.withDescription(errorCode.message).asException()
	}
}
