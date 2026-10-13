package com.ieum.web.response

import org.springframework.http.HttpStatus

class SingleResponse<T>(
	status: HttpStatus,
	val data: T,
) : BaseResponse(status)
