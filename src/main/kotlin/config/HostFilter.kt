package com.gonzalinux.config

import com.gonzalinux.common.SiteContextHolder.withPrefix
import com.gonzalinux.common.SiteContextHolder.withSite
import com.gonzalinux.config.SubdomainProperties
import com.gonzalinux.domain.site.SiteRepository
import com.gonzalinux.domain.site.SiteStatus
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Component
class HostFilter(
    private val siteRepository: SiteRepository,
    private val subdomainProperties: SubdomainProperties
) : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> {
        // The port is kept: dev sites are stored as `localhost:8080`.
        val domain = (request.headers().firstHeader("X-Site-Host")
            ?: request.headers().firstHeader("Host"))
            ?.let { subdomainProperties.normalizeHost(it) }
            ?: return ServerResponse.badRequest().build()


        val prefix = request.headers().firstHeader("X-Forwarded-Prefix")
            ?.trimStart('/')
            ?.takeIf { it.matches(Regex("^[a-zA-Z0-9-]{1,20}$")) }
            ?.let { "/$it" }
            ?: ""

        return siteRepository.findByDomain(domain)
            .filter { it.status == SiteStatus.VERIFIED || request.path().endsWith("/_verify") }
            .flatMap { site ->
                logger.debug { "Resolved site [siteId=${site.id}, domain=$domain, prefix=$prefix]" }
                next.handle(request).contextWrite { it.withSite(site).withPrefix(prefix) }
            }
            .switchIfEmpty(
                Mono.defer {
                    if (subdomainProperties.isHomeDomain(domain)) {
                        logger.debug { "Home domain: $domain, showing landing page" }
                        ServerResponse.ok().render("landing")
                    } else {
                        logger.debug { "No site found for domain: $domain, showing home page" }
                        ServerResponse.ok().render("home", mapOf("prefix" to prefix))
                    }
                }
            )
    }
}
