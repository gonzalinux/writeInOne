package com.gonzalinux.scheduler

import mu.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

private val logger = KotlinLogging.logger {}

abstract class SchedulerBase(private val intervalMs: Long) : ApplicationRunner, AutoCloseable {
    private var subscription: Disposable? = null

    abstract fun execute(): Mono<*>

    override fun run(args: ApplicationArguments) {
        subscription = Flux.interval(Duration.ZERO, Duration.ofMillis(intervalMs))
            .concatMap {
                execute()
                    .onErrorResume { e ->
                        logger.error(e) { "${this::class.simpleName} failed" }
                        Mono.empty()
                    }
            }
            .subscribe()
    }

    override fun close() {
        subscription?.dispose()
    }
}