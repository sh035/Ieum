package com.ieum.account.controller.dto.request

// 가입과 비밀번호 변경이 같은 규칙을 쓴다.
object PasswordPolicy {
	const val MIN = 8
	const val MAX = 16
	const val SIZE_MESSAGE = "Password must be between 8 and 16 characters."
	const val REGEXP = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$"
	const val REGEXP_MESSAGE = "Password must contain letters, digits and special characters."
}
