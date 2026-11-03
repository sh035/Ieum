package com.ieum.notification.scheduler

import com.ieum.notification.service.BriefingService
import com.ieum.scheduler.SchedulerConfig
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BriefingScheduler(
	private val briefingService: BriefingService,
) {

	@Scheduled(cron = "\${notification.scheduler.briefing-cron}", zone = SchedulerConfig.ZONE)
	@SchedulerLock(name = "notification-briefing", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
	fun run() {
		briefingService.sendBriefings()
	}
}
