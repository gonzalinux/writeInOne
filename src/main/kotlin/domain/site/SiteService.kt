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
class SiteService(
    private val repo: SiteRepository,
    private val verifyClient: VerifyClient,
    private val subdomainService: SubdomainService
) {

    fun create(userId: Long, request: CreateSiteRequest): Mono<Site> {
        val domain = subdomainService.normalizeDomain(request.domain)
        return request.toMono()
            .map { validateNavLinks(it.config); validateCustomCss(it.customCss); it }
            .flatMap { claimDomain(userId, domain) }
            .flatMap { status ->
                repo.create(
                    userId,
                    request.name,
                    domain,
                    request.description,
                    request.stylesUrl,
                    request.customCss?.trim() ?: "",
                    request.availableThemes,
                    request.languages,
                    request.config,
                    status
                )
            }
    }

    fun subdomainInfo(): SubdomainInfo = subdomainService.info()

    fun checkSubdomain(name: String, userId: Long): Mono<SubdomainInfo> = subdomainService.check(name, userId)

    /**
     * Validates [domain] for [userId] and returns the status a site on it starts in.
     * Subdomains of our own base domain need no DNS proof, so they go live immediately;
     * taking one also consumes any reservation the user was holding on the label.
     */
    private fun claimDomain(userId: Long, domain: String): Mono<SiteStatus> {
        if (subdomainService.isHomeDomain(domain))
            return Mono.error(BadRequestException("$domain is not available"))

        if (!subdomainService.isManaged(domain))
            return repo.existsByDomain(domain).flatMap { exists ->
                if (exists) Mono.error(SiteDomainTakenException(domain))
                else Mono.just(SiteStatus.NOT_VERIFIED)
            }

        return Mono.fromCallable { subdomainService.requireLabel(domain) }
            .flatMap { label -> subdomainService.ensureAvailable(label, userId) }
            .flatMap { label -> subdomainService.claim(label).thenReturn(SiteStatus.VERIFIED) }
    }

    fun list(userId: Long): Flux<Site> =
        repo.findAllByUserId(userId)

    fun findById(id: Long, userId: Long): Mono<Site> =
        repo.findById(id, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(id)))

    fun getAllUsers(id: Long, userId: Long): Flux<UserSite> =
        findById(id, userId)
            .flatMapMany {
                repo.getAllUsers(id)
            }

    fun deleteUser(id: Long, userIdToDelete: Long, userId: Long): Mono<Unit> {
        return findById(id, userId)
            .requireAdmin()
            .flatMap {
                when {
                    it.userId == userIdToDelete -> Mono.error { BadRequestException("It is not posible to remove the owner of the site.") }
                    // Not sure about this
                    userId == userIdToDelete -> Mono.error { BadRequestException("You can't remove yourself") }
                    else -> Mono.just(it)
                }
            }
            .flatMap { repo.deleteUser(it.id, userIdToDelete) }
    }

    fun updateUserRole(id: Long, userIdToUpdate: Long, role: Roles, userId: Long): Mono<Unit> {
        return findById(id, userId)
            .requireAdmin()
            .flatMap {
                when {
                    it.userId == userIdToUpdate -> Mono.error { BadRequestException("It is not posible to change the role of the owner of the site.") }
                    userId == userIdToUpdate -> Mono.error { BadRequestException("You can't change your own role") }
                    else -> Mono.just(it)
                }
            }
            .flatMap { repo.updateMemberRole(it.id, userIdToUpdate, role) }
    }

    fun update(id: Long, userId: Long, request: UpdateSiteRequest): Mono<Site> {
        return request.toMono()
            .map { validateNavLinks(it.config); validateCustomCss(it.customCss); it }
            .flatMap { repo.findById(id, userId) }
            .requireAdmin()
            .switchIfEmpty(Mono.error(SiteNotFoundException(id)))
            .flatMap { existing ->
                val newDomain = request.domain?.let { subdomainService.normalizeDomain(it) }
                val updated = if (newDomain == null || newDomain == existing.domain) {
                    // A managed subdomain is always verified, so re-verification is meaningless there.
                    val reset = request.requestVerification && !subdomainService.isManaged(existing.domain)
                    performUpdate(
                        id, request, domain = null,
                        status = if (reset) SiteStatus.NOT_VERIFIED else null,
                        prefix = prefixFor(existing.domain, request)
                    )
                } else {
                    claimDomain(userId, newDomain)
                        .flatMap { status ->
                            performUpdate(
                                id, request, domain = newDomain,
                                status = status,
                                prefix = prefixFor(newDomain, request)
                            )
                        }
                        // The old label stays parked so its owner can move back to it.
                        .flatMap { updated -> subdomainService.park(existing.domain, userId).thenReturn(updated) }
                }
                // repo.update doesn't join membership, so it comes back with a null role. Carry
                // the caller's over from the row we authorized against, otherwise the response
                // would look like a demotion to whoever just saved.
                updated.map { it.copy(role = existing.role) }
            }
    }

    private fun performUpdate(
        id: Long,
        request: UpdateSiteRequest,
        domain: String?,
        status: SiteStatus?,
        prefix: String?
    ): Mono<Site> =
        repo.update(
            id,
            request.name,
            domain,
            request.description,
            request.stylesUrl,
            request.customCss?.trim()?.ifBlank { "" },
            request.availableThemes,
            request.languages,
            request.config,
            status = status,
            prefix = prefix,
            verifyDate = if (status == SiteStatus.NOT_VERIFIED) OffsetDateTime.now() else null
        )

    /** Managed subdomains are served at the root — a proxy prefix would only corrupt their URLs. */
    private fun prefixFor(domain: String, request: UpdateSiteRequest): String? =
        if (subdomainService.isManaged(domain)) ""
        else request.prefix?.let { if (it.isNotEmpty() && !it.startsWith("/")) "/$it" else it }

    private fun validateCustomCss(css: String?) {
        if (css != null && css.length > 25_000)
            throw BadRequestException("Custom CSS exceeds the 25000 character limit")
    }

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
                        if (verified) repo.update(site.id, status = SiteStatus.VERIFIED)
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

    fun delete(id: Long, userId: Long): Mono<Unit> =
        repo.findById(id, userId)
            .requireAdmin()
            .switchIfEmpty(Mono.error(SiteNotFoundException(id)))
            // Park the label too, otherwise deleting a site frees the handle instantly.
            .flatMap { site ->
                // Parked for the site's owner, not whichever admin pressed delete.
                repo.delete(id).then(subdomainService.park(site.domain, site.userId)).thenReturn(Unit)
            }
}
