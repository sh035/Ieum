package com.ieum.scheduler

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock
import java.time.ZoneId
import javax.sql.DataSource

// Pod 가 여러 개여도 스케줄 작업은 한 Pod 에서만 돌도록 서비스 스키마의 shedlock 테이블로 락을 잡는다.
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
class SchedulerConfig {

	// Pod 마다 시계가 어긋나도 락 만료가 같도록 DB 시간을 기준으로 한다.
	@Bean
	fun lockProvider(
		dataSource: DataSource,
		@Value("\${spring.jpa.properties.hibernate.default_schema}") schema: String,
	): LockProvider = JdbcTemplateLockProvider(
		JdbcTemplateLockProvider.Configuration.builder()
			.withJdbcTemplate(JdbcTemplate(dataSource))
			.withTableName("$schema.shedlock")
			.usingDbTime()
			.build(),
	)

	// 마감 / 일정 시각은 한국 시간으로 저장되므로 서버 시간대와 상관없이 서울 기준으로 비교한다.
	@Bean
	fun clock(): Clock = Clock.system(SEOUL)

	companion object {
		const val ZONE = "Asia/Seoul"
		val SEOUL: ZoneId = ZoneId.of(ZONE)
	}
}
