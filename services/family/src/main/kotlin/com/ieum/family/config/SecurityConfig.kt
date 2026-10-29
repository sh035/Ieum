package com.ieum.family.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor
import org.springframework.grpc.server.security.GrpcSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

	@Bean
	fun filterChain(http: HttpSecurity): SecurityFilterChain {
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

	// gRPC 포트는 서비스 간 호출 전용이라 JWT 없이 받고, 접근 제한은 NetworkPolicy 로 한다.
	@Bean
	fun grpcAuthenticationInterceptor(grpc: GrpcSecurity): AuthenticationProcessInterceptor =
		grpc.authorizeRequests { it.allRequests().permitAll() }.build()
}
