package com.gonzalinux.scheduler

import com.gonzalinux.config.SchedulersProperties
import com.gonzalinux.config.SubdomainProperties
import com.gonzalinux.domain.site.SubdomainService
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SubdomainReservationScheduler(
    subdomainProperties: SubdomainProperties,
    schedulersProperties: SchedulersProperties,
    private val subdomainService: SubdomainService,
) : SchedulerBase(
    intervalMs = subdomainProperties.purgeIntervalMin * 60 * 1000,
    enabled = schedulersProperties.enabled
) {

    override fun execute(): Mono<*> = subdomainService.purgeExpired()
}
