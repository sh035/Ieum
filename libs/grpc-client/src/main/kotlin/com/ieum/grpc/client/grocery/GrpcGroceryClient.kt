package com.ieum.grpc.client.grocery

import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.grpc.client.GrpcClientSupport
import com.ieum.proto.grocery.v1.GetBriefingCountsRequest
import com.ieum.proto.grocery.v1.GroceryInternalServiceGrpc
import com.ieum.proto.grocery.v1.GroceryInternalServiceGrpc.GroceryInternalServiceBlockingStub
import org.springframework.grpc.client.GrpcChannelFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID

// 주소는 spring.grpc.client.channel.grocery.target 으로 정한다.
@Component
class GrpcGroceryClient(
	channels: GrpcChannelFactory,
) : GrpcClientSupport<GroceryInternalServiceBlockingStub>(
	GroceryInternalServiceGrpc.newBlockingStub(channels.createChannel("grocery")),
	GrpcClientErrorCode.GROCERY_SERVICE_UNAVAILABLE,
),
	GroceryClient {

	override fun getExpiringCounts(date: LocalDate): Map<UUID, Int> {
		val response = call { it.getBriefingCounts(GetBriefingCountsRequest.newBuilder().setDate(date.toString()).build()) }

		return response.expiringMap.entries.associate { (accountId, count) -> accountId.toUuid() to count }
	}
}
