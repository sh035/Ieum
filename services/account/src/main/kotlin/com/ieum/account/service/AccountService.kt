package com.ieum.account.service

import com.ieum.account.controller.dto.request.SignUpRequest
import com.ieum.account.domain.entity.Account
import com.ieum.account.exception.DuplicateEmailException
import com.ieum.account.repository.AccountRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AccountService(
	private val accountRepository: AccountRepository,
	private val passwordEncoder: PasswordEncoder,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun signUp(request: SignUpRequest) {
		if (accountRepository.existsByEmailAndIsDeletedFalse(request.email)) {
			throw DuplicateEmailException()
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
}
