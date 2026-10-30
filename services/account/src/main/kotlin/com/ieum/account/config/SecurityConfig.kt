package com.ieum.account.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor
import org.springframework.grpc.server.security.GrpcSecurity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.ObjectMapper

@Configuration
class SecurityConfig {

	@Bean
	fun filterChain(
		http: HttpSecurity,
		authenticationManager: AuthenticationManager,
		objectMapper: ObjectMapper,
		successHandler: CustomAuthenticationSuccessHandler,
		failureHandler: CustomAuthenticationFailureHandler,
	): SecurityFilterChain {
		// Filter를 빈으로 등록하면 Boot가 서블릿 체인에도 올리므로 여기서 직접 생성한다.
		val loginFilter = CustomAuthenticationFilter(authenticationManager, objectMapper).apply {
			setAuthenticationSuccessHandler(successHandler)
			setAuthenticationFailureHandler(failureHandler)
		}

		return http
			.csrf { it.disable() }
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.authorizeHttpRequests {
				it.requestMatchers("/api/v1/auth/**").permitAll()
				it.anyRequest().authenticated()
			}
			.oauth2ResourceServer {
				it.jwt { }
				it.authenticationEntryPoint(failureHandler)
			}
			.exceptionHandling { it.authenticationEntryPoint(failureHandler) }
			.addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter::class.java)
			.build()
	}

	@Bean
	fun authenticationManager(
		userDetailsService: UserDetailsService,
		passwordEncoder: PasswordEncoder,
	): AuthenticationManager =
		ProviderManager(
			DaoAuthenticationProvider(userDetailsService).apply { setPasswordEncoder(passwordEncoder) },
		)

	@Bean
	fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

	// gRPC 포트는 서비스 간 호출 전용이라 JWT 없이 받고, 접근 제한은 NetworkPolicy 로 한다.
	@Bean
	fun grpcAuthenticationInterceptor(grpc: GrpcSecurity): AuthenticationProcessInterceptor =
		grpc.authorizeRequests { it.allRequests().permitAll() }.build()
}
