package com.labtime.labtime

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LabtimeApplication

fun main(args: Array<String>) {
	runApplication<LabtimeApplication>(*args)
}
