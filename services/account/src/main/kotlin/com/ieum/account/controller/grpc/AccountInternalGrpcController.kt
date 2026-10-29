package com.ieum.account.controller.grpc

import com.ieum.account.service.AccountService
import com.ieum.proto.account.v1.AccountInternalServiceGrpc
import com.ieum.proto.account.v1.GetNicknamesRequest
import com.ieum.proto.account.v1.GetNicknamesResponse
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service
import java.util.UUID

// 서비스 간 호출 전용. Gateway 에 노출하지 않는다.
@Service
class AccountInternalGrpcController(
	private val accountService: AccountService,
) : AccountInternalServiceGrpc.AccountInternalServiceImplBase() {

	override fun getNicknames(request: GetNicknamesRequest, responseObserver: StreamObserver<GetNicknamesResponse>) {
		val accountIds = request.accountIdsList.map { runCatching { UUID.fromString(it) }.getOrNull() }
		if (accountIds.any { it == null }) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("invalid uuid").asRuntimeException())
			return
		}

		val profiles = accountService.getProfiles(accountIds.filterNotNull())
		responseObserver.onNext(
			GetNicknamesResponse.newBuilder()
				.putAllNicknames(profiles.associate { it.accountId.toString() to it.nickname })
				.build(),
		)
		responseObserver.onCompleted()
	}
}
