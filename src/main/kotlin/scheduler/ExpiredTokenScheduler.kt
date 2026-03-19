package com.gonzalinux.scheduler

import com.gonzalinux.config.TokenCleanerProperties
import com.gonzalinux.domain.user.UserRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class ExpiredTokenScheduler(
    private val tokenCleanerProperties: TokenCleanerProperties,
    private val userRepository: UserRepository,
    private val registry: MeterRegistry
) : SchedulerBase(tokenCleanerProperties.intervalMin * 60 * 1000) {

    override fun execute(): Mono<*> =
        userRepository.deleteExpiredTokens(tokenCleanerProperties.limitDeleted)
            .doOnSuccess { registry.counter("scheduler.token.cleanup.runs").increment() }
}