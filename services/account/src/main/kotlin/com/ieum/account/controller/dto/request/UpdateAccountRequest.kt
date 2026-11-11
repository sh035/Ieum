package com.ieum.account.controller.dto.request

import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

// 고친 필드만 보낸다. null 은 그대로 둔다는 뜻이다. 비밀번호를 바꿀 땐 currentPassword 도 보낸다.
data class UpdateAccountRequest(
	@Size(max = 20, message = "Nickname must not exceed 20 characters.")
	@Pattern(regexp = ".*\\S.*", message = "Nickname must not be blank.")
	val nickname: String? = null,

	@Past(message = "Birth date must be in the past.")
	val birthDate: LocalDate? = null,

	val currentPassword: String? = null,

	@Size(min = PasswordPolicy.MIN, max = PasswordPolicy.MAX, message = PasswordPolicy.SIZE_MESSAGE)
	@Pattern(regexp = PasswordPolicy.REGEXP, message = PasswordPolicy.REGEXP_MESSAGE)
	val newPassword: String? = null,
)
