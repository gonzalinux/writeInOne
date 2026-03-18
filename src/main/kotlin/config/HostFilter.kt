package com.gonzalinux.config

import com.gonzalinux.common.SiteContextHolder.withSite
import com.gonzalinux.domain.site.SiteRepository
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Component
class HostFilter(private val siteRepository: SiteRepository) : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> {
        val domain = request.headers().firstHeader("Host")
            ?.substringBefore(":")  // strip port if present
            ?: return ServerResponse.badRequest().build()

        return siteRepository.findByDomain(domain)
            .flatMap { site ->
                logger.debug { "Resolved site [siteId=${site.id}, domain=$domain]" }
                next.handle(request).contextWrite { it.withSite(site) }
            }
            .switchIfEmpty(
                Mono.defer {
                    logger.debug { "No site found for domain: $domain, showing landing page" }
                    ServerResponse.ok().render("landing")
                }
            )
    }
}
