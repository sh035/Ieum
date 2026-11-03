package com.ieum.planner.controller.grpc

import com.ieum.grpc.server.toLocalDate
import com.ieum.planner.service.BriefingCountService
import com.ieum.proto.planner.v1.BriefingCounts
import com.ieum.proto.planner.v1.GetBriefingCountsRequest
import com.ieum.proto.planner.v1.GetBriefingCountsResponse
import com.ieum.proto.planner.v1.PlannerInternalServiceGrpc
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service

// 서비스 간 호출 전용. Gateway 에 노출하지 않는다. 예외는 IeumGrpcExceptionHandler 가 gRPC 상태로 바꾼다.
@Service
class PlannerInternalGrpcController(
	private val briefingCountService: BriefingCountService,
) : PlannerInternalServiceGrpc.PlannerInternalServiceImplBase() {

	override fun getBriefingCounts(request: GetBriefingCountsRequest, responseObserver: StreamObserver<GetBriefingCountsResponse>) {
		val counts = briefingCountService.countsOn(request.date.toLocalDate())

		responseObserver.onNext(
			GetBriefingCountsResponse.newBuilder()
				.putAllCounts(
					counts.entries.associate { (accountId, count) ->
						accountId.toString() to BriefingCounts.newBuilder()
							.setTodo(count.todo)
							.setOverdueTodo(count.overdueTodo)
							.setSchedule(count.schedule)
							.build()
					},
				)
				.build(),
		)
		responseObserver.onCompleted()
	}
}
