package com.ieum.family

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.ieum"])
class FamilyApplication

fun main(args: Array<String>) {
	runApplication<FamilyApplication>(*args)
}
