package com.gonzalinux.scheduler

import com.gonzalinux.config.EmailTokenCleanerProperties
import com.gonzalinux.config.SchedulersProperties
import com.gonzalinux.domain.user.UserRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class ExpiredEmailTokenScheduler(
    private val props: EmailTokenCleanerProperties,
    schedulersProperties: SchedulersProperties,
    private val userRepository: UserRepository,
    private val registry: MeterRegistry
) : SchedulerBase(props.intervalMin * 60 * 1000, schedulersProperties.enabled) {

    override fun execute(): Mono<*> =
        userRepository.deleteExpiredEmailVerificationTokens(props.limitDeleted)
            .then(userRepository.deleteExpiredPasswordResetTokens(props.limitDeleted))
            .then(userRepository.deleteUnverifiedExpiredUsers())
            .doOnSuccess { registry.counter("scheduler.email.token.cleanup.runs").increment() }
}
