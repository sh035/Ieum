package com.ieum.web.response

import org.springframework.http.HttpStatus

// 컨벤션 30. currentPage 는 1부터 센다.
class PageResponse<T>(
	status: HttpStatus,
	val data: List<T>,
	val totalCount: Long,
	val currentPage: Int,
	val size: Int,
) : BaseResponse(status) {

	val totalPage: Int = ((totalCount + size - 1) / size).toInt()
}
