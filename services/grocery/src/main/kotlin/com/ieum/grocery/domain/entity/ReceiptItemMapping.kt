package com.ieum.grocery.domain.entity

import com.ieum.grocery.domain.enums.GroceryCategory
import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.util.UUID

@Entity
@Table(name = "receipt_item_mapping")
class ReceiptItemMapping(
	@Column(nullable = false, updatable = false)
	val familyId: UUID,

	@Column(nullable = false, updatable = false)
	val rawName: String,

	@Column(nullable = false)
	var name: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var category: GroceryCategory,

	@Column(nullable = false)
	var storageId: UUID,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val receiptItemMappingId: UUID? = null,
) : BaseEntity()
