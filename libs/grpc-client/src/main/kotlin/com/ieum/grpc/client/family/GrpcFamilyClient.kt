package com.ieum.grpc.client.family

import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.proto.family.v1.FamilyInternalServiceGrpc
import com.ieum.proto.family.v1.GetMemberRoleRequest
import com.ieum.proto.family.v1.GetMemberRolesRequest
import com.ieum.web.exception.IeumException
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.grpc.client.GrpcChannelFactory
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.ieum.proto.family.v1.FamilyRole as ProtoFamilyRole

// 주소는 spring.grpc.client.channel.family.target 으로 정한다.
@Component
class GrpcFamilyClient(
	channels: GrpcChannelFactory,
) : FamilyClient {

	private val stub = FamilyInternalServiceGrpc.newBlockingStub(channels.createChannel("family"))

	override fun getRole(familyId: UUID, accountId: UUID): FamilyRole? {
		val response = try {
			call {
				it.getMemberRole(
					GetMemberRoleRequest.newBuilder()
						.setFamilyId(familyId.toString())
						.setAccountId(accountId.toString())
						.build(),
				)
			}
		} catch (e: StatusRuntimeException) {
			if (e.status.code == Status.Code.NOT_FOUND) {
				return null
			}
			throw IeumException(GrpcClientErrorCode.FAMILY_SERVICE_UNAVAILABLE)
		}

		return response.role.toDomain()
	}

	override fun getRoles(familyId: UUID, accountIds: Collection<UUID>): Map<UUID, FamilyRole> {
		if (accountIds.isEmpty()) {
			return emptyMap()
		}

		val response = try {
			call {
				it.getMemberRoles(
					GetMemberRolesRequest.newBuilder()
						.setFamilyId(familyId.toString())
						.addAllAccountIds(accountIds.map(UUID::toString))
						.build(),
				)
			}
		} catch (e: StatusRuntimeException) {
			throw IeumException(GrpcClientErrorCode.FAMILY_SERVICE_UNAVAILABLE)
		}

		return response.rolesMap.entries.associate { (accountId, role) -> UUID.fromString(accountId) to role.toDomain() }
	}

	// deadline 은 호출 시점 기준이라 매번 새로 건다.
	private fun <T> call(rpc: (FamilyInternalServiceGrpc.FamilyInternalServiceBlockingStub) -> T): T =
		rpc(stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS))

	private fun ProtoFamilyRole.toDomain() = when (this) {
		ProtoFamilyRole.FAMILY_ROLE_OWNER -> FamilyRole.OWNER
		ProtoFamilyRole.FAMILY_ROLE_MANAGER -> FamilyRole.MANAGER
		ProtoFamilyRole.FAMILY_ROLE_MEMBER -> FamilyRole.MEMBER
		else -> throw IeumException(GrpcClientErrorCode.FAMILY_SERVICE_UNAVAILABLE)
	}

	companion object {
		private const val DEADLINE_SECONDS = 3L
	}
}
