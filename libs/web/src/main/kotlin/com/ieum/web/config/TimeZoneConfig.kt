package com.ieum.web.config

import org.springframework.context.annotation.Configuration
import java.time.ZoneId
import java.util.TimeZone

// 시각을 시간대 없이 한국 시간으로 저장하므로, 서버 시간대와 상관없이 LocalDateTime.now() 가 서울 시각이 되게 한다.
@Configuration
class TimeZoneConfig {

	init {
		TimeZone.setDefault(TimeZone.getTimeZone(SEOUL))
	}

	companion object {
		// @Scheduled 의 zone 처럼 상수가 필요한 곳에 쓴다.
		const val ZONE = "Asia/Seoul"
		val SEOUL: ZoneId = ZoneId.of(ZONE)
	}
}
