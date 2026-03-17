package com.gonzalinux.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner

private val logger = KotlinLogging.logger {}

abstract class SchedulerBase(private val intervalMs: Long) : ApplicationRunner {
    abstract suspend fun start()

    override fun run(args: ApplicationArguments) {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(intervalMs)
                runCatching { start() }
                    .onFailure { logger.error(it) { "${this@SchedulerBase::class.simpleName} failed" } }
            }
        }
    }
}