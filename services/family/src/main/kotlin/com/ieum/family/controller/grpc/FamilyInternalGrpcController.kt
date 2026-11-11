package com.ieum.family.controller.grpc

import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.service.FamilyMemberService
import com.ieum.family.service.FamilyService
import com.ieum.grpc.server.toUuid
import com.ieum.proto.family.v1.FamilyInternalServiceGrpc
import com.ieum.proto.family.v1.GetFamilyIdsRequest
import com.ieum.proto.family.v1.GetFamilyIdsResponse
import com.ieum.proto.family.v1.GetFamilyRequest
import com.ieum.proto.family.v1.GetFamilyResponse
import com.ieum.proto.family.v1.GetMemberRoleRequest
import com.ieum.proto.family.v1.GetMemberRoleResponse
import com.ieum.proto.family.v1.GetMemberRolesRequest
import com.ieum.proto.family.v1.GetMemberRolesResponse
import com.ieum.proto.family.v1.GetNicknamesRequest
import com.ieum.proto.family.v1.GetNicknamesResponse
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service
import java.util.UUID
import com.ieum.proto.family.v1.FamilyMember as ProtoFamilyMember
import com.ieum.proto.family.v1.FamilyRole as ProtoFamilyRole

// 서비스 간 호출 전용. Gateway 에 노출하지 않는다. 예외는 IeumGrpcExceptionHandler 가 gRPC 상태로 바꾼다.
@Service
class FamilyInternalGrpcController(
	private val familyService: FamilyService,
	private val familyMemberService: FamilyMemberService,
) : FamilyInternalServiceGrpc.FamilyInternalServiceImplBase() {

	override fun getMemberRole(request: GetMemberRoleRequest, responseObserver: StreamObserver<GetMemberRoleResponse>) {
		val role = familyMemberService.getRole(request.familyId.toUuid(), request.accountId.toUuid()).role

		responseObserver.onNext(GetMemberRoleResponse.newBuilder().setRole(role.toProto()).build())
		responseObserver.onCompleted()
	}

	override fun getMemberRoles(request: GetMemberRolesRequest, responseObserver: StreamObserver<GetMemberRolesResponse>) {
		val familyId = request.familyId.toUuid()
		val accountIds = request.accountIdsList.map { it.toUuid() }

		val roles = familyMemberService.getRoles(familyId, accountIds)
		responseObserver.onNext(
			GetMemberRolesResponse.newBuilder()
				.putAllRoles(roles.entries.associate { (accountId, role) -> accountId.toString() to role.toProto() })
				.build(),
		)
		responseObserver.onCompleted()
	}

	override fun getFamily(request: GetFamilyRequest, responseObserver: StreamObserver<GetFamilyResponse>) {
		val family = familyService.getFamilyInfo(request.familyId.toUuid())

		responseObserver.onNext(
			GetFamilyResponse.newBuilder()
				.setName(family.name)
				.addAllMembers(
					family.members.map { (accountId, role) ->
						ProtoFamilyMember.newBuilder().setAccountId(accountId.toString()).setRole(role.toProto()).build()
					},
				)
				.build(),
		)
		responseObserver.onCompleted()
	}

	override fun getFamilyIds(request: GetFamilyIdsRequest, responseObserver: StreamObserver<GetFamilyIdsResponse>) {
		val familyIds = familyMemberService.getFamilyIds(request.accountId.toUuid())

		responseObserver.onNext(GetFamilyIdsResponse.newBuilder().addAllFamilyIds(familyIds.map(UUID::toString)).build())
		responseObserver.onCompleted()
	}

	override fun getNicknames(request: GetNicknamesRequest, responseObserver: StreamObserver<GetNicknamesResponse>) {
		val nicknames = familyMemberService.getNicknames(request.familyId.toUuid(), request.accountIdsList.map { it.toUuid() })

		responseObserver.onNext(
			GetNicknamesResponse.newBuilder()
				.putAllNicknames(nicknames.entries.associate { (accountId, nickname) -> accountId.toString() to nickname })
				.build(),
		)
		responseObserver.onCompleted()
	}

	private fun FamilyRole.toProto() = ProtoFamilyRole.valueOf("FAMILY_ROLE_$name")
}
