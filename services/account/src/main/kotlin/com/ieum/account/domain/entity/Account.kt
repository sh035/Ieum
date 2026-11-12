package com.ieum.account.domain.entity

import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDate
import java.util.UUID

// 바뀐 칼럼만 쓴다. 내 정보 수정과 탈퇴가 겹쳐도 수정이 읽어 둔 is_deleted 로 탈퇴를 되돌리지 않는다.
@DynamicUpdate
@Entity
@Table(name = "account")
class Account(
	@Column(nullable = false)
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
