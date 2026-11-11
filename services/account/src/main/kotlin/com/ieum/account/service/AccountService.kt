package com.ieum.account.service

import com.ieum.account.controller.dto.request.SignUpRequest
import com.ieum.account.controller.dto.request.UpdateAccountRequest
import com.ieum.account.controller.dto.response.AccountProfileResponse
import com.ieum.account.controller.dto.response.AccountResponse
import com.ieum.account.controller.dto.response.TokenResponse
import com.ieum.account.controller.dto.response.UpdateAccountResponse
import com.ieum.account.domain.entity.Account
import com.ieum.account.exception.AccountErrorCode
import com.ieum.account.repository.AccountRepository
import com.ieum.account.repository.RefreshTokenRepository
import com.ieum.account.util.JwtProvider
import com.ieum.web.exception.IeumException
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AccountService(
	private val accountRepository: AccountRepository,
	private val refreshTokenRepository: RefreshTokenRepository,
	private val passwordEncoder: PasswordEncoder,
	private val jwtProvider: JwtProvider,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun signUp(request: SignUpRequest) {
		if (accountRepository.existsByEmailAndIsDeletedFalse(request.email)) {
			throw IeumException(AccountErrorCode.DUPLICATE_EMAIL)
		}

		val account = accountRepository.save(
			Account(
				email = request.email,
				password = requireNotNull(passwordEncoder.encode(request.password)),
				nickname = request.nickname,
				birthDate = request.birthDate,
			),
		)

		log.debug("Account created. accountId={}", account.accountId)
	}

	fun issueTokens(accountId: String): TokenResponse =
		TokenResponse(
			accessToken = jwtProvider.createAccessToken(accountId),
			refreshToken = refreshTokenRepository.issue(accountId),
		)

	fun reissue(refreshToken: String): TokenResponse {
		val accountId = refreshTokenRepository.findAccountId(refreshToken)
			?: throw IeumException(AccountErrorCode.INVALID_CREDENTIALS)

		return TokenResponse(
			accessToken = jwtProvider.createAccessToken(accountId),
			refreshToken = refreshToken,
		)
	}

	fun logout(refreshToken: String) {
		refreshTokenRepository.revoke(refreshToken)
	}

	fun getAccount(accountId: String): AccountResponse = findActive(accountId).toResponse()

	// 보낸 필드만 바꾼다. 비밀번호를 바꾸면 모든 기기의 refresh token 을 끊고 지금 기기에만 새로 발급한다.
	fun updateAccount(accountId: String, request: UpdateAccountRequest): UpdateAccountResponse {
		val account = findActive(accountId)
		request.newPassword?.let { newPassword ->
			val currentPassword = request.currentPassword ?: throw IeumException(AccountErrorCode.CURRENT_PASSWORD_REQUIRED)
			if (!passwordEncoder.matches(currentPassword, account.password)) {
				throw IeumException(AccountErrorCode.WRONG_CURRENT_PASSWORD)
			}
			account.password = requireNotNull(passwordEncoder.encode(newPassword))
		}
		request.nickname?.let { account.nickname = it }
		request.birthDate?.let { account.birthDate = it }

		val saved = accountRepository.save(account)
		if (request.newPassword == null) {
			return UpdateAccountResponse(account = saved.toResponse(), token = null)
		}

		refreshTokenRepository.revokeAll(accountId)
		log.debug("Password changed. accountId={}", accountId)
		return UpdateAccountResponse(account = saved.toResponse(), token = issueTokens(accountId))
	}

	private fun findActive(accountId: String): Account =
		accountRepository.findById(UUID.fromString(accountId))
			.filter { !it.isDeleted }
			.orElseThrow { IeumException(AccountErrorCode.ACCOUNT_NOT_FOUND) }

	private fun Account.toResponse() = AccountResponse(email = email, nickname = nickname, birthDate = birthDate)

	// 없거나 탈퇴한 계정이면 ACCOUNT_NOT_FOUND 다.
	fun findProfileByEmail(email: String): AccountProfileResponse {
		val account = accountRepository.findByEmailAndIsDeletedFalse(email) ?: throw IeumException(AccountErrorCode.ACCOUNT_NOT_FOUND)
		return AccountProfileResponse(accountId = requireNotNull(account.accountId), nickname = account.nickname)
	}

	// 없거나 탈퇴한 계정은 응답에서 빠진다.
	fun getProfiles(accountIds: List<UUID>): List<AccountProfileResponse> =
		accountRepository.findAllByAccountIdInAndIsDeletedFalse(accountIds)
			.map { AccountProfileResponse(accountId = requireNotNull(it.accountId), nickname = it.nickname) }
}
