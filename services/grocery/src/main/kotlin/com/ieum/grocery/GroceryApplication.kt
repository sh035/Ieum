package com.ieum.grocery

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GroceryApplication

fun main(args: Array<String>) {
	runApplication<GroceryApplication>(*args)
}
