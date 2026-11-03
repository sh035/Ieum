package com.ieum.grpc.client.planner

import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.grpc.client.GrpcClientSupport
import com.ieum.proto.planner.v1.GetBriefingCountsRequest
import com.ieum.proto.planner.v1.PlannerInternalServiceGrpc
import com.ieum.proto.planner.v1.PlannerInternalServiceGrpc.PlannerInternalServiceBlockingStub
import org.springframework.grpc.client.GrpcChannelFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID

// 주소는 spring.grpc.client.channel.planner.target 으로 정한다.
@Component
class GrpcPlannerClient(
	channels: GrpcChannelFactory,
) : GrpcClientSupport<PlannerInternalServiceBlockingStub>(
	PlannerInternalServiceGrpc.newBlockingStub(channels.createChannel("planner")),
	GrpcClientErrorCode.PLANNER_SERVICE_UNAVAILABLE,
),
	PlannerClient {

	override fun getBriefingCounts(date: LocalDate): Map<UUID, PlannerBriefingCounts> {
		val response = call { it.getBriefingCounts(GetBriefingCountsRequest.newBuilder().setDate(date.toString()).build()) }

		return response.countsMap.entries.associate { (accountId, counts) ->
			accountId.toUuid() to PlannerBriefingCounts(todo = counts.todo, overdueTodo = counts.overdueTodo, schedule = counts.schedule)
		}
	}
}
