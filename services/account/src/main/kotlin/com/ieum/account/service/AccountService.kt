package com.ieum.account.service

import com.ieum.account.controller.dto.request.LoginRequest
import com.ieum.account.controller.dto.request.SignUpRequest
import com.ieum.account.controller.dto.response.AccountResponse
import com.ieum.account.controller.dto.response.TokenResponse
import com.ieum.account.domain.entity.Account
import com.ieum.account.exception.AccountErrorCode
import com.ieum.account.repository.AccountRepository
import com.ieum.account.repository.RefreshTokenRepository
import com.ieum.security.jwt.JwtProvider
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

	fun login(request: LoginRequest): TokenResponse {
		val account = accountRepository.findByEmailAndIsDeletedFalse(request.email)
			?: throw IeumException(AccountErrorCode.INVALID_CREDENTIALS)

		if (!passwordEncoder.matches(request.password, account.password)) {
			throw IeumException(AccountErrorCode.INVALID_CREDENTIALS)
		}

		val accountId = requireNotNull(account.accountId).toString()

		return TokenResponse(
			accessToken = jwtProvider.createAccessToken(accountId),
			refreshToken = refreshTokenRepository.issue(accountId),
		)
	}

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

	fun getAccount(accountId: String): AccountResponse {
		val account = accountRepository.findById(UUID.fromString(accountId))
			.filter { !it.isDeleted }
			.orElseThrow { IeumException(AccountErrorCode.ACCOUNT_NOT_FOUND) }

		return AccountResponse(
			email = account.email,
			nickname = account.nickname,
			birthDate = account.birthDate,
		)
	}
}
