package com.ieum.grpc.client

import com.ieum.web.exception.ErrorCode
import com.ieum.web.exception.IeumException
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.AbstractStub
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

// 클라이언트마다 반복되는 deadline 과 실패 변환을 묶는다. 실패는 호출한 API 의 503 으로 바꾼다.
abstract class GrpcClientSupport<S : AbstractStub<S>>(
	private val stub: S,
	private val unavailableCode: ErrorCode,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// deadline 은 호출 시점 기준이라 매번 새로 건다.
	protected fun <T> call(rpc: (S) -> T): T =
		try {
			rpc(stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS))
		} catch (e: StatusRuntimeException) {
			log.warn("gRPC call failed. status={}", e.status)
			unavailable()
		}

	// 대상이 없으면 null 이다.
	protected fun <T> callOrNull(rpc: (S) -> T): T? =
		try {
			rpc(stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS))
		} catch (e: StatusRuntimeException) {
			if (e.status.code == Status.Code.NOT_FOUND) {
				null
			} else {
				log.warn("gRPC call failed. status={}", e.status)
				unavailable()
			}
		}

	// 상대 서비스가 보낸 잘못된 값은 상대 장애로 본다.
	protected fun String.toUuid(): UUID = runCatching { UUID.fromString(this) }.getOrElse { unavailable() }

	protected fun unavailable(): Nothing = throw IeumException(unavailableCode)

	companion object {
		private const val DEADLINE_SECONDS = 3L
	}
}
