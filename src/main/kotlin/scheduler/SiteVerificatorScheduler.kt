package com.gonzalinux.scheduler

import com.gonzalinux.config.SiteVerificatorProperties
import com.gonzalinux.domain.site.SiteService
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SiteVerificatorScheduler(
    siteVerificatorProperties: SiteVerificatorProperties,
    private val siteService: SiteService,
) : SchedulerBase(intervalMs = siteVerificatorProperties.intervalSec * 1000L) {

    override fun execute(): Mono<*> = siteService.verifyPendingSites()
}
