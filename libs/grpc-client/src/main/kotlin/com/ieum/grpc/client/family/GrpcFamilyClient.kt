package com.ieum.grpc.client.family

import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.grpc.client.GrpcClientSupport
import com.ieum.proto.family.v1.FamilyInternalServiceGrpc
import com.ieum.proto.family.v1.FamilyInternalServiceGrpc.FamilyInternalServiceBlockingStub
import com.ieum.proto.family.v1.GetFamilyRequest
import com.ieum.proto.family.v1.GetMemberRoleRequest
import com.ieum.proto.family.v1.GetMemberRolesRequest
import org.springframework.grpc.client.GrpcChannelFactory
import org.springframework.stereotype.Component
import java.util.UUID
import com.ieum.proto.family.v1.FamilyRole as ProtoFamilyRole

// 주소는 spring.grpc.client.channel.family.target 으로 정한다.
@Component
class GrpcFamilyClient(
	channels: GrpcChannelFactory,
) : GrpcClientSupport<FamilyInternalServiceBlockingStub>(
	FamilyInternalServiceGrpc.newBlockingStub(channels.createChannel("family")),
	GrpcClientErrorCode.FAMILY_SERVICE_UNAVAILABLE,
),
	FamilyClient {

	override fun getRole(familyId: UUID, accountId: UUID): FamilyRole? =
		callOrNull {
			it.getMemberRole(
				GetMemberRoleRequest.newBuilder()
					.setFamilyId(familyId.toString())
					.setAccountId(accountId.toString())
					.build(),
			)
		}?.role?.toDomain()

	override fun getRoles(familyId: UUID, accountIds: Collection<UUID>): Map<UUID, FamilyRole> {
		if (accountIds.isEmpty()) {
			return emptyMap()
		}

		val response = call {
			it.getMemberRoles(
				GetMemberRolesRequest.newBuilder()
					.setFamilyId(familyId.toString())
					.addAllAccountIds(accountIds.map(UUID::toString))
					.build(),
			)
		}

		return response.rolesMap.entries.associate { (accountId, role) -> accountId.toUuid() to role.toDomain() }
	}

	override fun getFamily(familyId: UUID): FamilyInfo? {
		val response = callOrNull { it.getFamily(GetFamilyRequest.newBuilder().setFamilyId(familyId.toString()).build()) }
			?: return null

		return FamilyInfo(
			name = response.name,
			members = response.membersList.associate { it.accountId.toUuid() to it.role.toDomain() },
		)
	}

	private fun ProtoFamilyRole.toDomain() = when (this) {
		ProtoFamilyRole.FAMILY_ROLE_OWNER -> FamilyRole.OWNER
		ProtoFamilyRole.FAMILY_ROLE_MANAGER -> FamilyRole.MANAGER
		ProtoFamilyRole.FAMILY_ROLE_MEMBER -> FamilyRole.MEMBER
		else -> unavailable()
	}
}
