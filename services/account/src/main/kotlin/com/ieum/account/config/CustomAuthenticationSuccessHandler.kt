package com.ieum.account.config

import com.ieum.account.service.AccountService
import com.ieum.web.response.BaseResponseEntity
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class CustomAuthenticationSuccessHandler(
	private val accountService: AccountService,
	private val objectMapper: ObjectMapper,
) : AuthenticationSuccessHandler {

	override fun onAuthenticationSuccess(
		request: HttpServletRequest,
		response: HttpServletResponse,
		authentication: Authentication,
	) {
		// 이 핸들러는 응답을 직접 쓰기 때문에, 컨트롤러처럼 code/message/timestamp가 자동으로 붙지 않는다.
		// 로그인 응답만 다른 모양이 되지 않도록 컨트롤러와 같은 BaseResponseEntity로 본문을 만든다.
		val entity = BaseResponseEntity.single(accountService.issueTokens(authentication.name))

		response.status = entity.statusCode.value()
		response.contentType = MediaType.APPLICATION_JSON_VALUE
		response.writer.write(objectMapper.writeValueAsString(entity.body))
	}
}
