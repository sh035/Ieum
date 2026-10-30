package com.ieum.scheduler

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

// 스케줄 작업마다 어디까지 처리했는지 한 행으로 남긴다. 인프라 테이블이라 공통 컬럼을 두지 않는다.
@Entity
@Table(name = "scheduler_checkpoint")
class SchedulerCheckpoint(
	@Id
	val name: String,

	@Column(nullable = false)
	var processedUntil: LocalDateTime,
)
