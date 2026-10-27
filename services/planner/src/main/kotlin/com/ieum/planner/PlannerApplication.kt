package com.ieum.planner

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.ieum"])
class PlannerApplication

fun main(args: Array<String>) {
	runApplication<PlannerApplication>(*args)
}
