package com.ieum.account.controller.grpc

import com.ieum.account.service.AccountService
import com.ieum.grpc.server.toUuid
import com.ieum.proto.account.v1.AccountInternalServiceGrpc
import com.ieum.proto.account.v1.GetNicknamesRequest
import com.ieum.proto.account.v1.GetNicknamesResponse
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service

// 서비스 간 호출 전용. Gateway 에 노출하지 않는다. 예외는 IeumGrpcExceptionHandler 가 gRPC 상태로 바꾼다.
@Service
class AccountInternalGrpcController(
	private val accountService: AccountService,
) : AccountInternalServiceGrpc.AccountInternalServiceImplBase() {

	override fun getNicknames(request: GetNicknamesRequest, responseObserver: StreamObserver<GetNicknamesResponse>) {
		val profiles = accountService.getProfiles(request.accountIdsList.map { it.toUuid() })

		responseObserver.onNext(
			GetNicknamesResponse.newBuilder()
				.putAllNicknames(profiles.associate { it.accountId.toString() to it.nickname })
				.build(),
		)
		responseObserver.onCompleted()
	}
}
