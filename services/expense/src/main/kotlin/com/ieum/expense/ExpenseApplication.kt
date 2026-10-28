package com.ieum.expense

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.ieum"])
class ExpenseApplication

fun main(args: Array<String>) {
	runApplication<ExpenseApplication>(*args)
}
