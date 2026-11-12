package com.ieum.grpc.client.family

import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.grpc.client.GrpcClientSupport
import com.ieum.proto.family.v1.CheckWithdrawalRequest
import com.ieum.proto.family.v1.FamilyInternalServiceGrpc
import com.ieum.proto.family.v1.FamilyInternalServiceGrpc.FamilyInternalServiceBlockingStub
import com.ieum.proto.family.v1.GetFamilyIdsRequest
import com.ieum.proto.family.v1.GetFamilyRequest
import com.ieum.proto.family.v1.GetMemberRoleRequest
import com.ieum.proto.family.v1.GetMemberRolesRequest
import com.ieum.proto.family.v1.LeaveAllFamiliesRequest
import com.ieum.proto.family.v1.GetMemberNicknamesRequest
import org.springframework.grpc.client.GrpcChannelFactory
import org.springframework.stereotype.Component
import java.util.UUID
import com.ieum.proto.family.v1.FamilyRole as ProtoFamilyRole
import com.ieum.proto.family.v1.FamilySummary as ProtoFamilySummary
import com.ieum.proto.family.v1.WithdrawalCheck as ProtoWithdrawalCheck

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

	override fun getFamilyIds(accountId: UUID): List<UUID> =
		call { it.getFamilyIds(GetFamilyIdsRequest.newBuilder().setAccountId(accountId.toString()).build()) }
			.familyIdsList.map { it.toUuid() }

	override fun getNicknames(familyId: UUID, accountIds: Collection<UUID>): Map<UUID, String> {
		if (accountIds.isEmpty()) {
			return emptyMap()
		}

		val response = call {
			it.getMemberNicknames(
				GetMemberNicknamesRequest.newBuilder()
					.setFamilyId(familyId.toString())
					.addAllAccountIds(accountIds.map(UUID::toString))
					.build(),
			)
		}

		return response.nicknamesMap.entries.associate { (accountId, nickname) -> accountId.toUuid() to nickname }
	}

	override fun checkWithdrawal(accountId: UUID): WithdrawalCheck =
		call { it.checkWithdrawal(CheckWithdrawalRequest.newBuilder().setAccountId(accountId.toString()).build()) }.toDomain()

	override fun leaveAllFamilies(accountId: UUID, deleteSoloFamilies: Boolean): LeaveAllFamiliesResult {
		val response = call {
			it.leaveAllFamilies(
				LeaveAllFamiliesRequest.newBuilder()
					.setAccountId(accountId.toString())
					.setDeleteSoloFamilies(deleteSoloFamilies)
					.build(),
			)
		}

		return LeaveAllFamiliesResult(left = response.left, check = response.check.toDomain())
	}

	private fun ProtoWithdrawalCheck.toDomain() = WithdrawalCheck(
		transferRequiredFamilies = transferRequiredFamiliesList.map { it.toDomain() },
		soloFamilies = soloFamiliesList.map { it.toDomain() },
	)

	private fun ProtoFamilySummary.toDomain() = FamilySummary(familyId = familyId.toUuid(), name = name)

	private fun ProtoFamilyRole.toDomain() = when (this) {
		ProtoFamilyRole.FAMILY_ROLE_OWNER -> FamilyRole.OWNER
		ProtoFamilyRole.FAMILY_ROLE_MANAGER -> FamilyRole.MANAGER
		ProtoFamilyRole.FAMILY_ROLE_MEMBER -> FamilyRole.MEMBER
		else -> unavailable()
	}
}
