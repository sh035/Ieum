package com.ieum.security.config

import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

// 토큰을 직접 발급하지 않는 서비스는 이 클래스를 상속해 account 가 발급한 JWT 로 인증한다.
abstract class ResourceServerSecurityConfig {

	@Bean
	open fun filterChain(http: HttpSecurity): SecurityFilterChain {
		return http
			.csrf { it.disable() }
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.authorizeHttpRequests {
				// 배포 전까지 인증 체크 없이 전체 허용
				it.anyRequest().permitAll()
			}
			.oauth2ResourceServer { it.jwt { } }
			.build()
	}
}
