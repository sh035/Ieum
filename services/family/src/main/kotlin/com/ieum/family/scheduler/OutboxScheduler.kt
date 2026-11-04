package com.ieum.family.scheduler

import com.ieum.family.service.OutboxRelayService
import com.ieum.scheduler.SchedulerConfig
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OutboxScheduler(
	private val outboxRelayService: OutboxRelayService,
) {

	@Scheduled(cron = "\${family.scheduler.outbox-relay-cron}", zone = SchedulerConfig.ZONE)
	@SchedulerLock(name = "family-outbox-relay", lockAtMostFor = "PT1M")
	fun relay() {
		outboxRelayService.relay()
	}

	@Scheduled(cron = "\${family.scheduler.outbox-cleanup-cron}", zone = SchedulerConfig.ZONE)
	@SchedulerLock(name = "family-outbox-cleanup", lockAtMostFor = "PT10M")
	fun cleanup() {
		outboxRelayService.cleanup()
	}
}
