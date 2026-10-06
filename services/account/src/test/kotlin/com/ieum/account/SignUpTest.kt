package com.ieum.account

import com.ieum.account.controller.dto.request.SignUpRequest
import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignUpTest {

	private val validator = Validation.buildDefaultValidatorFactory().validator

	private fun validate(
		email: String = "tobi@ieum.com",
		password: String = "Pass123!",
		nickname: String = "tobi",
		birthDate: LocalDate = LocalDate.of(1995, 3, 21),
	) = validator.validate(SignUpRequest(email, password, nickname, birthDate))

	@Test
	fun `valid request passes`() {
		assertTrue(validate().isEmpty())
		assertTrue(validate(email = "a.b+c@ieum.co.kr").isEmpty())
		assertTrue(validate(password = "Abcd1234!@#\$%^&").isEmpty())
	}

	@Test
	fun `invalid email is rejected`() {
		assertEquals(1, validate(email = "notanemail").size)
		assertEquals(1, validate(email = "a@b").size)
		assertEquals(1, validate(email = "a@b.c").size)
	}

	@Test
	fun `password requires letters digits and special characters`() {
		assertEquals(1, validate(password = "Password!").size)
		assertEquals(1, validate(password = "Password1").size)
		assertEquals(1, validate(password = "1234567!").size)
	}

	@Test
	fun `password length must be between 8 and 16`() {
		assertTrue(validate(password = "Pa1!").isNotEmpty())
		assertTrue(validate(password = "Abcdefg123456789!").isNotEmpty())
	}

	@Test
	fun `blank nickname is rejected`() {
		assertTrue(validate(nickname = "").isNotEmpty())
	}

	@Test
	fun `future birth date is rejected`() {
		assertTrue(validate(birthDate = LocalDate.now().plusDays(1)).isNotEmpty())
	}

	@Test
	fun `messages are in english`() {
		val message = validate(email = "notanemail").first().message
		assertEquals("Email format is invalid.", message)
	}
}
