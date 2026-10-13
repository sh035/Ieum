package com.ieum.account.config

import com.ieum.account.controller.dto.request.LoginRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import tools.jackson.databind.ObjectMapper

// formLogin은 form 파라미터만 읽으므로 JSON 요청을 받는 필터를 직접 둔다.
class CustomAuthenticationFilter(
	authenticationManager: AuthenticationManager,
	private val objectMapper: ObjectMapper,
) : AbstractAuthenticationProcessingFilter(
	PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/login"),
	authenticationManager,
) {

	override fun attemptAuthentication(
		request: HttpServletRequest,
		response: HttpServletResponse,
	): Authentication {
		val login = try {
			objectMapper.readValue(request.inputStream, LoginRequest::class.java)
		} catch (e: Exception) {
			throw AuthenticationServiceException("Malformed login request.", e)
		}

		return authenticationManager.authenticate(
			UsernamePasswordAuthenticationToken.unauthenticated(login.email, login.password),
		)
	}
}
