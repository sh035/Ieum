package com.ieum.grpc.client.grocery

import com.ieum.grpc.client.InProcessGrpcTestSupport
import com.ieum.proto.grocery.v1.GetBriefingCountsRequest
import com.ieum.proto.grocery.v1.GetBriefingCountsResponse
import com.ieum.proto.grocery.v1.GroceryInternalServiceGrpc
import com.ieum.web.exception.IeumException
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GrpcGroceryClientTest : InProcessGrpcTestSupport() {

	private val accountId = UUID.randomUUID()
	private val date = LocalDate.of(2026, 10, 30)

	private fun clientRespondingWith(respond: (GetBriefingCountsRequest, StreamObserver<GetBriefingCountsResponse>) -> Unit) =
		GrpcGroceryClient(
			channelsFor(object : GroceryInternalServiceGrpc.GroceryInternalServiceImplBase() {
				override fun getBriefingCounts(request: GetBriefingCountsRequest, observer: StreamObserver<GetBriefingCountsResponse>) {
					respond(request, observer)
				}
			}),
		)

	@Test
	fun `날짜를 yyyy-MM-dd 로 보내고 계정별 유통기한 개수를 돌려준다`() {
		var asked = ""
		val client = clientRespondingWith { request, observer ->
			asked = request.date
			observer.onNext(GetBriefingCountsResponse.newBuilder().putExpiring(accountId.toString(), 2).build())
			observer.onCompleted()
		}

		assertEquals(mapOf(accountId to 2), client.getExpiringCounts(date))
		assertEquals("2026-10-30", asked)
	}

	@Test
	fun `grocery 오류면 503`() {
		val client = clientRespondingWith { _, observer -> observer.onError(Status.INTERNAL.asRuntimeException()) }

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertFailsWith<IeumException> { client.getExpiringCounts(date) }.errorCode.status)
	}
}
