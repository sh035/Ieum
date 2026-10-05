package com.ieum.account.exception

import com.ieum.web.exception.IeumException
import org.springframework.http.HttpStatus

class DuplicateEmailException : IeumException(
	status = HttpStatus.CONFLICT,
	message = "Email already exists.",
)
