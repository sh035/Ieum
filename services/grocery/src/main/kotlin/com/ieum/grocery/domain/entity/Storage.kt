package com.ieum.grocery.domain.entity

import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.util.UUID

@Entity
@Table(name = "storage")
class Storage(
	@Column(nullable = false, updatable = false)
	val familyId: UUID,

	@Column(nullable = false)
	var name: String,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val storageId: UUID? = null,
) : BaseEntity()
