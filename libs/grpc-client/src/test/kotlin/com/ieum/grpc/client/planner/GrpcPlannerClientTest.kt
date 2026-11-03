package com.ieum.grpc.client.planner

import com.ieum.grpc.client.InProcessGrpcTestSupport
import com.ieum.proto.planner.v1.BriefingCounts
import com.ieum.proto.planner.v1.GetBriefingCountsRequest
import com.ieum.proto.planner.v1.GetBriefingCountsResponse
import com.ieum.proto.planner.v1.PlannerInternalServiceGrpc
import com.ieum.web.exception.IeumException
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GrpcPlannerClientTest : InProcessGrpcTestSupport() {

	private val accountId = UUID.randomUUID()
	private val date = LocalDate.of(2026, 10, 30)

	private fun clientRespondingWith(respond: (GetBriefingCountsRequest, StreamObserver<GetBriefingCountsResponse>) -> Unit) =
		GrpcPlannerClient(
			channelsFor(object : PlannerInternalServiceGrpc.PlannerInternalServiceImplBase() {
				override fun getBriefingCounts(request: GetBriefingCountsRequest, observer: StreamObserver<GetBriefingCountsResponse>) {
					respond(request, observer)
				}
			}),
		)

	@Test
	fun `날짜를 yyyy-MM-dd 로 보내고 계정별 개수를 도메인 타입으로 바꾼다`() {
		var asked = ""
		val client = clientRespondingWith { request, observer ->
			asked = request.date
			observer.onNext(
				GetBriefingCountsResponse.newBuilder()
					.putCounts(accountId.toString(), BriefingCounts.newBuilder().setTodo(2).setOverdueTodo(1).setSchedule(3).build())
					.build(),
			)
			observer.onCompleted()
		}

		assertEquals(mapOf(accountId to PlannerBriefingCounts(todo = 2, overdueTodo = 1, schedule = 3)), client.getBriefingCounts(date))
		assertEquals("2026-10-30", asked)
	}

	@Test
	fun `planner 오류나 잘못된 계정 id 면 503`() {
		val failing = clientRespondingWith { _, observer -> observer.onError(Status.INTERNAL.asRuntimeException()) }
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertFailsWith<IeumException> { failing.getBriefingCounts(date) }.errorCode.status)
		tearDown()

		val broken = clientRespondingWith { _, observer ->
			observer.onNext(GetBriefingCountsResponse.newBuilder().putCounts("not-uuid", BriefingCounts.getDefaultInstance()).build())
			observer.onCompleted()
		}
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertFailsWith<IeumException> { broken.getBriefingCounts(date) }.errorCode.status)
	}
}
