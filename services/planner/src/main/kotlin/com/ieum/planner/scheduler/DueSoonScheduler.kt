package com.ieum.planner.scheduler

import com.ieum.planner.service.DueSoonService
import com.ieum.web.config.TimeZoneConfig
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DueSoonScheduler(
	private val dueSoonService: DueSoonService,
) {

	// 1분마다 돈다. 처리 범위는 체크포인트가 이어 주므로 회차를 건너뛰어도 다음 회차가 따라잡는다.
	// Pod 끼리 시계가 조금 어긋나도 같은 분에 두 번 돌지 않게 락을 30초는 유지한다.
	@Scheduled(cron = "\${planner.scheduler.due-soon-cron}", zone = TimeZoneConfig.ZONE)
	@SchedulerLock(name = "planner-due-soon", lockAtMostFor = "PT50S", lockAtLeastFor = "PT30S")
	fun run() {
		dueSoonService.notifyDueSoon()
	}
}
