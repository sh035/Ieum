package com.ieum.grocery.controller.grpc

import com.ieum.grocery.service.BriefingCountService
import com.ieum.grpc.server.toLocalDate
import com.ieum.proto.grocery.v1.GetBriefingCountsRequest
import com.ieum.proto.grocery.v1.GetBriefingCountsResponse
import com.ieum.proto.grocery.v1.GroceryInternalServiceGrpc
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service

// 서비스 간 호출 전용. Gateway 에 노출하지 않는다. 예외는 IeumGrpcExceptionHandler 가 gRPC 상태로 바꾼다.
@Service
class GroceryInternalGrpcController(
	private val briefingCountService: BriefingCountService,
) : GroceryInternalServiceGrpc.GroceryInternalServiceImplBase() {

	override fun getBriefingCounts(request: GetBriefingCountsRequest, responseObserver: StreamObserver<GetBriefingCountsResponse>) {
		val counts = briefingCountService.expiringCountsOn(request.date.toLocalDate())

		responseObserver.onNext(
			GetBriefingCountsResponse.newBuilder()
				.putAllExpiring(counts.mapKeys { (accountId, _) -> accountId.toString() })
				.build(),
		)
		responseObserver.onCompleted()
	}
}
