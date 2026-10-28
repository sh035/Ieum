package com.ieum.grocery.domain.entity

import com.ieum.grocery.domain.enums.GroceryCategory
import com.ieum.grocery.domain.enums.Visibility
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
@Table(name = "grocery_item")
class GroceryItem(
	@Column(nullable = false)
	var storageId: UUID,

	@Column(nullable = false, updatable = false)
	val familyId: UUID,

	@Column(nullable = false, updatable = false)
	val creatorAccountId: UUID,

	@Column(nullable = false)
	var name: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var category: GroceryCategory,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	val visibility: Visibility,

	@Column(nullable = false)
	var quantity: Int,

	var purchasePrice: Int?,

	var purchaseDate: LocalDate?,

	var expiryDate: LocalDate?,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val groceryItemId: UUID? = null,
) : BaseEntity()
