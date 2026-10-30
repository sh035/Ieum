package com.ieum.planner.scheduler

import com.ieum.planner.service.DueSoonService
import com.ieum.scheduler.SchedulerConfig
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DueSoonScheduler(
	private val dueSoonService: DueSoonService,
) {

	// 1분마다 돌고 창을 2분으로 잡아 한 번 건너뛰어도 놓치지 않는다. 겹친 알림은 dedup 키로 걸러진다.
	@Scheduled(cron = "\${planner.scheduler.due-soon-cron}", zone = SchedulerConfig.ZONE)
	@SchedulerLock(name = "planner-due-soon", lockAtMostFor = "PT50S")
	fun run() {
		dueSoonService.notifyDueSoon()
	}
}
