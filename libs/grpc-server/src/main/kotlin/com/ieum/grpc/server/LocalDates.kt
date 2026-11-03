package com.ieum.grpc.server

import com.ieum.web.exception.IeumException
import java.time.LocalDate

// 요청의 yyyy-MM-dd 문자열을 바꾼다. 형식이 틀리면 INVALID_ARGUMENT 로 응답된다.
fun String.toLocalDate(): LocalDate =
	runCatching { LocalDate.parse(this) }.getOrElse { throw IeumException(GrpcServerErrorCode.INVALID_DATE) }
