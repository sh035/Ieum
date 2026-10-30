package com.ieum.web.response

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class PageResponseTest {

	@Test
	fun `전체 건수와 크기로 전체 페이지를 계산한다`() {
		val response = PageResponse(HttpStatus.OK, data = listOf("a"), totalCount = 41, currentPage = 3, size = 20)

		assertEquals(3, response.totalPage)
		assertEquals(listOf("a"), response.data)
		assertEquals(3, response.currentPage)
		assertEquals(20, response.size)
	}

	@Test
	fun `목록이 없으면 빈 배열과 전체 페이지 0`() {
		val response = PageResponse(HttpStatus.OK, data = emptyList<String>(), totalCount = 0, currentPage = 1, size = 20)

		assertEquals(0, response.totalPage)
		assertEquals(emptyList(), response.data)
	}
}
