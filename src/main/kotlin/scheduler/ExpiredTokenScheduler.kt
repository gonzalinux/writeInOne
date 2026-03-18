package com.gonzalinux.scheduler

import com.gonzalinux.config.TokenCleanerProperties
import com.gonzalinux.domain.user.UserRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class ExpiredTokenScheduler(
    private val tokenCleanerProperties: TokenCleanerProperties,
    private val userRepository: UserRepository
) : SchedulerBase(tokenCleanerProperties.intervalMin * 60 * 1000) {

    override fun execute(): Mono<*> =
        userRepository.deleteExpiredTokens(tokenCleanerProperties.limitDeleted)
}