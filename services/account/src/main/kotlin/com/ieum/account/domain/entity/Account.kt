package com.ieum.account.domain.entity

import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "account")
class Account(
	@Column(nullable = false, unique = true)
	val email: String,

	@Column(nullable = false)
	var password: String,

	@Column(nullable = false)
	var nickname: String,

	@Column(nullable = false)
	var birthDate: LocalDate,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val accountId: UUID? = null,
) : BaseEntity()
