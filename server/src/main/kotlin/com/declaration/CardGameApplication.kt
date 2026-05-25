package com.declaration

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CardGameApplication

fun main(args: Array<String>) {
    runApplication<CardGameApplication>(*args)
}
