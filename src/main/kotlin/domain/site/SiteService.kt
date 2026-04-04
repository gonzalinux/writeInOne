package com.gonzalinux.domain.site

import com.gonzalinux.api.data.CreateSiteRequest
import com.gonzalinux.api.data.UpdateSiteRequest
import com.gonzalinux.common.BadRequestException
import com.gonzalinux.common.SiteDomainTakenException
import com.gonzalinux.common.SiteNotFoundException
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.OffsetDateTime

private val logger = KotlinLogging.logger {}
@Service
class SiteService(private val repo: SiteRepository, private val verifyClient: VerifyClient) {

    fun create(userId: Long, request: CreateSiteRequest): Mono<Site> {
        return request.toMono()
            .map { validateNavLinks(it.config) }
            .flatMap { repo.existsByDomain(request.domain) }
            .flatMap { exists ->
                if (exists) Mono.error(SiteDomainTakenException(request.domain))
                else repo.create(
                    userId,
                    request.name,
                    request.domain,
                    request.description,
                    request.stylesUrl,
                    request.availableThemes,
                    request.languages,
                    request.config
                )
            }
    }

    fun list(userId: Long): Flux<Site> =
        repo.findAllByUserId(userId)

    fun findById(id: Long, userId: Long): Mono<Site> =
        repo.findById(id, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(id)))

    fun update(id: Long, userId: Long, request: UpdateSiteRequest): Mono<Site> {
        return request.toMono()
            .map { validateNavLinks(it.config) }
            .flatMap {
                if (request.domain != null) {
                    repo.findById(id, userId)
                        .switchIfEmpty(Mono.error(SiteNotFoundException(id)))
                        .flatMap { existing ->
                            if (existing.domain == request.domain) {
                                performUpdate(id, userId, request, resetVerification = request.requestVerification)
                            } else {
                                repo.existsByDomain(request.domain)
                                    .flatMap { exists ->
                                        if (exists) Mono.error(SiteDomainTakenException(request.domain))
                                        else performUpdate(id, userId, request, resetVerification = true)
                                    }
                            }
                        }
                } else {
                    performUpdate(id, userId, request, resetVerification = request.requestVerification)
                        .switchIfEmpty(Mono.error(SiteNotFoundException(id)))
                }
            }
    }

    private fun performUpdate(id: Long, userId: Long, request: UpdateSiteRequest, resetVerification: Boolean = false): Mono<Site> =
        repo.update(
            id,
            userId,
            request.name,
            request.domain,
            request.description,
            request.stylesUrl,
            request.availableThemes,
            request.languages,
            request.config,
            status = if (resetVerification) SiteStatus.NOT_VERIFIED else null,
            verifyDate = if (resetVerification) OffsetDateTime.now() else null
        )

    private fun validateNavLinks(config: SiteConfig?) {
        config ?: return
        val allLinks = config.en.nav + config.es.nav
        allLinks.forEach { link ->
            if (!link.url.startsWith("http://") && !link.url.startsWith("https://") && !link.url.startsWith("/")) {
                throw BadRequestException("Nav link URL '${link.url}' must start with http://, https://, or /")
            }
        }
    }

    fun verifyPendingSites(): Mono<Void> =
        repo.notVerified()
            .flatMap { site ->
                logger.info { "Attempting verification  ${site.domain}" }
                verifyClient.verify(site.domain, site.prefix)
                    .flatMap { verified ->
                        if (verified) repo.update(site.id, site.userId, status = SiteStatus.VERIFIED)
                        else Mono.empty()
                    }
                    .onErrorResume { e ->
                        logger.warn { "Verification failed for ${site.domain}: ${e.message}" }
                        Mono.empty()
                    }
            }
            .doOnNext {
                logger.info { "Verified  ${it.domain}" }
            }
            .then()

    fun delete(id: Long, userId: Long): Mono<Void> =
        repo.findById(id, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(id)))
            .flatMap { repo.delete(id, userId) }
}
