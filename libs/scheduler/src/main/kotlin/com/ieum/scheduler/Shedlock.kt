package com.ieum.scheduler

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

// ShedLock 이 쓰는 락 테이블이다. 라이브러리가 정한 구조라 공통 컬럼과 PK 이름 규칙을 따르지 않는다. 락은 LockProvider 가 잡으므로 서비스 코드에서 고치지 않는다.
@Entity
@Table(name = "shedlock")
class Shedlock(
	@Id
	val name: String,

	@Column(nullable = false)
	var lockUntil: LocalDateTime,

	@Column(nullable = false)
	var lockedAt: LocalDateTime,

	@Column(nullable = false)
	var lockedBy: String,
)
