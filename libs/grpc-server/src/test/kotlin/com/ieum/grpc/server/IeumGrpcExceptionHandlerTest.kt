package com.ieum.grpc.server

import com.ieum.web.exception.ErrorCode
import com.ieum.web.exception.IeumException
import io.grpc.Status
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IeumGrpcExceptionHandlerTest {

	private val handler = IeumGrpcExceptionHandler()

	private enum class TestErrorCode(override val status: HttpStatus, override val message: String) : ErrorCode {
		NOT_FOUND(HttpStatus.NOT_FOUND, "Family not found."),
		BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad input."),
		CONFLICT(HttpStatus.CONFLICT, "Conflict."),
	}

	@Test
	fun `404 는 NOT_FOUND 로 바꾸고 메시지를 담는다`() {
		val status = requireNotNull(handler.handleException(IeumException(TestErrorCode.NOT_FOUND))).status

		assertEquals(Status.Code.NOT_FOUND, status.code)
		assertEquals("Family not found.", status.description)
	}

	@Test
	fun `400 은 INVALID_ARGUMENT 로 바꾼다`() {
		assertEquals(
			Status.Code.INVALID_ARGUMENT,
			requireNotNull(handler.handleException(IeumException(TestErrorCode.BAD_REQUEST))).status.code,
		)
	}

	@Test
	fun `매핑하지 않은 상태와 다른 예외는 기본 처리에 맡긴다`() {
		assertNull(handler.handleException(IeumException(TestErrorCode.CONFLICT)))
		assertNull(handler.handleException(IllegalArgumentException("server bug")))
	}

	@Test
	fun `UUID 형식이 아니면 400 IeumException`() {
		val e = kotlin.runCatching { "not-a-uuid".toUuid() }.exceptionOrNull() as IeumException

		assertEquals(HttpStatus.BAD_REQUEST, e.errorCode.status)
	}
}
