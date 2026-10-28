package com.ieum.expense.domain.entity

import com.ieum.expense.domain.enums.ExpenseCategory
import com.ieum.expense.domain.enums.Visibility
import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "expense")
class Expense(
	@Column(nullable = false, updatable = false)
	val familyId: UUID,

	@Column(nullable = false, updatable = false)
	val creatorAccountId: UUID,

	@Column(nullable = false)
	var payerAccountId: UUID,

	@Column(nullable = false)
	var title: String,

	var memo: String?,

	@Column(nullable = false)
	var amount: Int,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var category: ExpenseCategory,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	val visibility: Visibility,

	@Column(nullable = false)
	var spentDate: LocalDate,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val expenseId: UUID? = null,
) : BaseEntity()
