package com.ieum.grpc.client

import com.ieum.web.exception.ErrorCode
import com.ieum.web.exception.IeumException
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.AbstractStub
import java.util.concurrent.TimeUnit

// 클라이언트마다 반복되는 deadline 과 실패 변환을 묶는다. 실패는 호출한 API 의 503 으로 바꾼다.
abstract class GrpcClientSupport<S : AbstractStub<S>>(
	private val stub: S,
	private val unavailable: ErrorCode,
) {

	// deadline 은 호출 시점 기준이라 매번 새로 건다.
	protected fun <T> call(rpc: (S) -> T): T =
		try {
			rpc(stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS))
		} catch (e: StatusRuntimeException) {
			unavailable()
		}

	// 대상이 없으면 null 이다.
	protected fun <T> callOrNull(rpc: (S) -> T): T? =
		try {
			rpc(stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS))
		} catch (e: StatusRuntimeException) {
			if (e.status.code == Status.Code.NOT_FOUND) null else unavailable()
		}

	protected fun unavailable(): Nothing = throw IeumException(unavailable)

	companion object {
		private const val DEADLINE_SECONDS = 3L
	}
}
