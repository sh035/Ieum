package com.ieum.scheduler

import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime

// 스케줄 작업이 어디까지 처리했는지 기록해, 다음 회차가 그 지점부터 이어서 처리하게 한다. 중복도 누락도 없다.
// 호출하는 쪽 트랜잭션에 함께 묶여서, 처리 중 실패하면 기록도 롤백되고 다음 회차가 다시 처리한다.
@Service
class SchedulerCheckpointService(
	private val schedulerCheckpointRepository: SchedulerCheckpointRepository,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// (기록한 지점, until] 을 처리한다. 처음이거나 오래 멈췄다면 until - maxCatchUp 까지만 거슬러 올라간다.
	fun process(name: String, until: LocalDateTime, maxCatchUp: Duration, block: (from: LocalDateTime, until: LocalDateTime) -> Unit) {
		val floor = until.minus(maxCatchUp)
		val checkpoint = schedulerCheckpointRepository.findByIdOrNull(name)
		val from = maxOf(checkpoint?.processedUntil ?: floor, floor)
		if (from >= until) return
		if (checkpoint != null && checkpoint.processedUntil < floor) {
			log.warn("Scheduler window skipped. name={}, from={}, to={}", name, checkpoint.processedUntil, floor)
		}

		block(from, until)
		schedulerCheckpointRepository.save(checkpoint?.apply { processedUntil = until } ?: SchedulerCheckpoint(name, until))
	}
}
