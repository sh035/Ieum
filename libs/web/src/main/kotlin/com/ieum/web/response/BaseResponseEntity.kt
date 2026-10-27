package com.ieum.web.response

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

class BaseResponseEntity : ResponseEntity<BaseResponse> {

	constructor(status: HttpStatus) : super(status)

	constructor(body: BaseResponse, status: HttpStatus) : super(body, status)

	companion object {

		fun <T> single(body: T): BaseResponseEntity =
			BaseResponseEntity(SingleResponse(HttpStatus.OK, body), HttpStatus.OK)

		fun created(): BaseResponseEntity =
			BaseResponseEntity(HttpStatus.CREATED)

		fun <T> created(body: T): BaseResponseEntity =
			BaseResponseEntity(SingleResponse(HttpStatus.CREATED, body), HttpStatus.CREATED)

		fun emptyNoContent(): BaseResponseEntity =
			BaseResponseEntity(HttpStatus.NO_CONTENT)
	}
}
