package com.ieum.shopping

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ShoppingApplication

fun main(args: Array<String>) {
	runApplication<ShoppingApplication>(*args)
}
