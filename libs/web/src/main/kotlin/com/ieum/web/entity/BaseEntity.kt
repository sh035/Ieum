package com.ieum.web.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.LocalDateTime
import java.util.UUID

@MappedSuperclass
abstract class BaseEntity {

	@Column(nullable = false, updatable = false)
	val createdAt: LocalDateTime = LocalDateTime.now()

	@Column(updatable = false)
	var createdBy: UUID? = null
		protected set

	var updatedAt: LocalDateTime? = null
		protected set

	var updatedBy: UUID? = null
		protected set

	@Column(nullable = false)
	var isDeleted: Boolean = false
		protected set

	@PrePersist
	protected fun onCreate() {
		createdBy = currentAccountId()
	}

	@PreUpdate
	protected fun onUpdate() {
		updatedAt = LocalDateTime.now()
		updatedBy = currentAccountId()
	}

	fun delete() {
		isDeleted = true
	}

	// 토큰으로 들어온 요청만 계정을 남긴다. 회원가입, 이벤트 처리, 스케줄러, 서비스 간 gRPC 는 null(시스템)이다.
	private fun currentAccountId(): UUID? =
		(SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)?.name?.let(UUID::fromString)
}
