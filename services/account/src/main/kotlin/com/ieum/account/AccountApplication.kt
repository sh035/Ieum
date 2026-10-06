package com.ieum.account

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.ieum"])
class AccountApplication

fun main(args: Array<String>) {
	runApplication<AccountApplication>(*args)
}
