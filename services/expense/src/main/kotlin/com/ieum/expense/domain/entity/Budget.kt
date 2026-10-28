package com.ieum.expense.domain.entity

import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "budget")
class Budget(
	@Column(nullable = false, updatable = false)
	val familyId: UUID,

	@Column(updatable = false)
	val accountId: UUID?,

	@Column(updatable = false)
	val budgetMonth: LocalDate?,

	@Column(nullable = false)
	var amount: Int,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val budgetId: UUID? = null,
) : BaseEntity()
