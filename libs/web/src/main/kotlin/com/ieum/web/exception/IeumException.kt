package com.ieum.web.exception

class IeumException(
	val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
