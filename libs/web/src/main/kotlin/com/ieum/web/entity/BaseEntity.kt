package com.ieum.web.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PreUpdate
import java.time.LocalDateTime

@MappedSuperclass
abstract class BaseEntity {

	@Column(nullable = false, updatable = false)
	val createdAt: LocalDateTime = LocalDateTime.now()

	var updatedAt: LocalDateTime? = null
		protected set

	@Column(nullable = false)
	var isDeleted: Boolean = false
		protected set

	@PreUpdate
	protected fun onUpdate() {
		updatedAt = LocalDateTime.now()
	}

	fun delete() {
		isDeleted = true
	}
}
