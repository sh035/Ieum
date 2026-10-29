package com.ieum.family.controller.grpc

import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.service.FamilyMemberService
import com.ieum.family.service.FamilyService
import com.ieum.proto.family.v1.FamilyInternalServiceGrpc
import com.ieum.proto.family.v1.FamilyMember
import com.ieum.proto.family.v1.GetFamilyRequest
import com.ieum.proto.family.v1.GetFamilyResponse
import com.ieum.proto.family.v1.GetMemberRoleRequest
import com.ieum.proto.family.v1.GetMemberRoleResponse
import com.ieum.proto.family.v1.GetMemberRolesRequest
import com.ieum.proto.family.v1.GetMemberRolesResponse
import com.ieum.web.exception.IeumException
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.UUID
import com.ieum.proto.family.v1.FamilyRole as ProtoFamilyRole

// 서비스 간 호출 전용. Gateway 에 노출하지 않는다.
@Service
class FamilyInternalGrpcController(
	private val familyService: FamilyService,
	private val familyMemberService: FamilyMemberService,
) : FamilyInternalServiceGrpc.FamilyInternalServiceImplBase() {

	override fun getMemberRole(request: GetMemberRoleRequest, responseObserver: StreamObserver<GetMemberRoleResponse>) {
		val familyId = request.familyId.toUuidOrNull()
		val accountId = request.accountId.toUuidOrNull()
		if (familyId == null || accountId == null) {
			responseObserver.onError(invalidUuid())
			return
		}

		val role = responseObserver.orNotFound { familyMemberService.getRole(familyId, accountId).role } ?: return

		responseObserver.onNext(GetMemberRoleResponse.newBuilder().setRole(role.toProto()).build())
		responseObserver.onCompleted()
	}

	override fun getMemberRoles(request: GetMemberRolesRequest, responseObserver: StreamObserver<GetMemberRolesResponse>) {
		val familyId = request.familyId.toUuidOrNull()
		val accountIds = request.accountIdsList.map { it.toUuidOrNull() }
		if (familyId == null || accountIds.any { it == null }) {
			responseObserver.onError(invalidUuid())
			return
		}

		val roles = familyMemberService.getRoles(familyId, accountIds.filterNotNull())
		responseObserver.onNext(
			GetMemberRolesResponse.newBuilder()
				.putAllRoles(roles.entries.associate { (accountId, role) -> accountId.toString() to role.toProto() })
				.build(),
		)
		responseObserver.onCompleted()
	}

	override fun getFamily(request: GetFamilyRequest, responseObserver: StreamObserver<GetFamilyResponse>) {
		val familyId = request.familyId.toUuidOrNull()
		if (familyId == null) {
			responseObserver.onError(invalidUuid())
			return
		}

		val family = responseObserver.orNotFound { familyService.getFamilyInfo(familyId) } ?: return
		responseObserver.onNext(
			GetFamilyResponse.newBuilder()
				.setName(family.name)
				.addAllMembers(
					family.members.map { (accountId, role) ->
						FamilyMember.newBuilder().setAccountId(accountId.toString()).setRole(role.toProto()).build()
					},
				)
				.build(),
		)
		responseObserver.onCompleted()
	}

	// 404 는 NOT_FOUND 로 응답하고 null 을 돌려준다. 그 밖의 예외는 그대로 던진다.
	private fun <T> StreamObserver<*>.orNotFound(block: () -> T): T? =
		try {
			block()
		} catch (e: IeumException) {
			if (e.errorCode.status != HttpStatus.NOT_FOUND) {
				throw e
			}
			onError(Status.NOT_FOUND.asRuntimeException())
			null
		}

	private fun FamilyRole.toProto() = ProtoFamilyRole.valueOf("FAMILY_ROLE_$name")

	private fun invalidUuid() = Status.INVALID_ARGUMENT.withDescription("invalid uuid").asRuntimeException()

	private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
