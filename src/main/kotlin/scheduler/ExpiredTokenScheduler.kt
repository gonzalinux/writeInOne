package com.gonzalinux.scheduler

import com.gonzalinux.config.TokenCleanerProperties
import com.gonzalinux.domain.user.UserRepository
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.stereotype.Component

@Component
class ExpiredTokenScheduler(
    private val tokenCleanerProperties: TokenCleanerProperties,
    private val userRepository: UserRepository
) : SchedulerBase(tokenCleanerProperties.intervalMin * 60 * 1000) {

    override suspend fun start() {
        userRepository.deleteExpiredTokens(tokenCleanerProperties.limitDeleted)
            .awaitSingleOrNull()
    }
}