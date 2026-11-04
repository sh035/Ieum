package com.ieum.grpc.server

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor
import org.springframework.grpc.server.security.GrpcSecurity

// gRPC 포트는 서비스 간 호출 전용이라 JWT 없이 받고, 접근 제한은 NetworkPolicy 로 한다.
@Configuration
class GrpcSecurityConfig {

	@Bean
	fun grpcAuthenticationInterceptor(grpc: GrpcSecurity): AuthenticationProcessInterceptor =
		grpc.authorizeRequests { it.allRequests().permitAll() }.build()
}
