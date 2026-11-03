package com.ieum.notification

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.ieum"])
class NotificationApplication

fun main(args: Array<String>) {
	runApplication<NotificationApplication>(*args)
}
