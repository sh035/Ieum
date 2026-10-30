package com.ieum.grpc.server

import com.ieum.web.exception.IeumException
import java.util.UUID

// 요청의 UUID 문자열을 바꾼다. 형식이 틀리면 INVALID_ARGUMENT 로 응답된다.
fun String.toUuid(): UUID =
	runCatching { UUID.fromString(this) }.getOrElse { throw IeumException(GrpcServerErrorCode.INVALID_UUID) }
