package com.gonzalinux.scheduler

import com.gonzalinux.config.SchedulersProperties
import com.gonzalinux.config.SiteVerificatorProperties
import com.gonzalinux.domain.site.SiteService
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SiteVerificatorScheduler(
    siteVerificatorProperties: SiteVerificatorProperties,
    schedulersProperties: SchedulersProperties,
    private val siteService: SiteService,
) : SchedulerBase(intervalMs = siteVerificatorProperties.intervalSec * 1000L, enabled = schedulersProperties.enabled) {

    override fun execute(): Mono<*> = siteService.verifyPendingSites()
}
