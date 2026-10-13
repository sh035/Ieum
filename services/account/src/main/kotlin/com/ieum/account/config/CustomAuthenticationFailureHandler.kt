package com.ieum.account.config

import com.ieum.account.exception.AccountErrorCode
import com.ieum.web.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

// 필터 계층의 인증 실패는 GlobalExceptionInterceptor가 잡지 못해 응답을 여기서 직접 만든다.
// 로그인 실패(FailureHandler)와 토큰 없는 요청(EntryPoint)이 같은 401 응답이라 한 클래스로 둔다.
@Component
class CustomAuthenticationFailureHandler(
	private val objectMapper: ObjectMapper,
) : AuthenticationFailureHandler, AuthenticationEntryPoint {

	override fun onAuthenticationFailure(
		request: HttpServletRequest,
		response: HttpServletResponse,
		exception: AuthenticationException,
	) = write(response)

	override fun commence(
		request: HttpServletRequest,
		response: HttpServletResponse,
		authException: AuthenticationException,
	) = write(response)

	private fun write(response: HttpServletResponse) {
		val errorCode = AccountErrorCode.INVALID_CREDENTIALS

		response.status = errorCode.status.value()
		response.contentType = MediaType.APPLICATION_JSON_VALUE
		response.writer.write(
			objectMapper.writeValueAsString(
				ErrorResponse(code = errorCode.status.value(), message = errorCode.message),
			),
		)
	}
}
