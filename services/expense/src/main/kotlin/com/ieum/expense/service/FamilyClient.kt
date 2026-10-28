package com.ieum.expense.service

import com.ieum.expense.domain.enums.FamilyRole
import com.ieum.expense.exception.ExpenseErrorCode
import com.ieum.web.exception.IeumException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

@Component
class FamilyClient(
	restClientBuilder: RestClient.Builder,
	@Value("\${family.url}") familyUrl: String,
) {

	private val restClient = restClientBuilder.baseUrl(familyUrl).build()

	fun isMember(familyId: UUID, accountId: UUID): Boolean = getRole(familyId, accountId) != null

	// 구성원이 아니면 null 이다.
	fun getRole(familyId: UUID, accountId: UUID): FamilyRole? =
		try {
			restClient.get()
				.uri("/internal/family/{familyId}/member/{accountId}/role", familyId, accountId)
				.retrieve()
				.body(RoleResponse::class.java)
				?.data
				?.role
				?: throw IeumException(ExpenseErrorCode.FAMILY_SERVICE_UNAVAILABLE)
		} catch (e: HttpClientErrorException) {
			if (e.statusCode != HttpStatus.NOT_FOUND) {
				throw IeumException(ExpenseErrorCode.FAMILY_SERVICE_UNAVAILABLE)
			}
			null
		} catch (e: RestClientException) {
			throw IeumException(ExpenseErrorCode.FAMILY_SERVICE_UNAVAILABLE)
		}

	data class RoleResponse(val data: RoleData)

	data class RoleData(val role: FamilyRole)
}
