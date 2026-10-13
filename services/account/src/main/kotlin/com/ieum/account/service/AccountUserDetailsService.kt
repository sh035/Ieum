package com.ieum.account.service

import com.ieum.account.repository.AccountRepository
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class AccountUserDetailsService(
	private val accountRepository: AccountRepository,
) : UserDetailsService {

	// username 자리에 accountId를 담는다. 인증 이후 핸들러가 토큰 subject로 그대로 쓴다.
	override fun loadUserByUsername(email: String): UserDetails {
		val account = accountRepository.findByEmailAndIsDeletedFalse(email)
			?: throw UsernameNotFoundException("Account not found.")

		return User.withUsername(requireNotNull(account.accountId).toString())
			.password(account.password)
			.authorities(emptyList<GrantedAuthority>())
			.build()
	}
}
