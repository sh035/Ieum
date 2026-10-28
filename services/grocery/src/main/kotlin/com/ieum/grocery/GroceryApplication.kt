package com.ieum.grocery

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.ieum"])
class GroceryApplication

fun main(args: Array<String>) {
	runApplication<GroceryApplication>(*args)
}
