package com.gonzalinux.scheduler

import com.gonzalinux.config.SchedulersProperties
import com.gonzalinux.config.TokenCleanerProperties
import com.gonzalinux.domain.site.SiteInvitationRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/** Reuses the refresh-token cleanup cadence — same shape of problem (opaque token + expires_at). */
@Component
class ExpiredInvitationScheduler(
    private val tokenCleanerProperties: TokenCleanerProperties,
    schedulersProperties: SchedulersProperties,
    private val repo: SiteInvitationRepository,
    private val registry: MeterRegistry
) : SchedulerBase(tokenCleanerProperties.intervalMin * 60 * 1000, schedulersProperties.enabled) {

    override fun execute(): Mono<*> =
        repo.deleteExpired(tokenCleanerProperties.limitDeleted)
            .doOnSuccess { registry.counter("scheduler.invitation.cleanup.runs").increment() }
}
