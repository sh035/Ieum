package com.ieum.grpc.client.account

import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.grpc.client.GrpcClientSupport
import com.ieum.proto.account.v1.AccountInternalServiceGrpc
import com.ieum.proto.account.v1.AccountInternalServiceGrpc.AccountInternalServiceBlockingStub
import com.ieum.proto.account.v1.GetNicknamesRequest
import org.springframework.grpc.client.GrpcChannelFactory
import org.springframework.stereotype.Component
import java.util.UUID

// 주소는 spring.grpc.client.channel.account.target 으로 정한다.
@Component
class GrpcAccountClient(
	channels: GrpcChannelFactory,
) : GrpcClientSupport<AccountInternalServiceBlockingStub>(
	AccountInternalServiceGrpc.newBlockingStub(channels.createChannel("account")),
	GrpcClientErrorCode.ACCOUNT_SERVICE_UNAVAILABLE,
),
	AccountClient {

	override fun getNicknames(accountIds: Collection<UUID>): Map<UUID, String> {
		if (accountIds.isEmpty()) {
			return emptyMap()
		}

		val response = call {
			it.getNicknames(GetNicknamesRequest.newBuilder().addAllAccountIds(accountIds.map(UUID::toString)).build())
		}

		return response.nicknamesMap.entries.associate { (accountId, nickname) -> accountId.toUuid() to nickname }
	}
}
