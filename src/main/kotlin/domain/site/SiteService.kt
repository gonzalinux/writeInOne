package com.gonzalinux.domain.site

import com.gonzalinux.api.data.CreateSiteRequest
import com.gonzalinux.api.data.UpdateSiteRequest
import com.gonzalinux.common.BadRequestException
import com.gonzalinux.common.SiteDomainTakenException
import com.gonzalinux.common.SiteNotFoundException
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class SiteService(private val repo: SiteRepository) {

    fun create(userId: Long, request: CreateSiteRequest): Mono<Site> {
        validateNavLinks(request.config)
        return repo.existsByDomain(request.domain)
            .flatMap { exists ->
                if (exists) Mono.error(SiteDomainTakenException(request.domain))
                else repo.create(userId, request.name, request.domain, request.description, request.stylesUrl, request.availableThemes, request.languages, request.config)
            }
    }

    fun list(userId: Long): Flux<Site> =
        repo.findAllByUserId(userId)

    fun findById(id: Long, userId: Long): Mono<Site> =
        repo.findById(id, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(id)))

    fun update(id: Long, userId: Long, request: UpdateSiteRequest): Mono<Site> {
        validateNavLinks(request.config)
        return repo.update(id, userId, request.name, request.description, request.stylesUrl, request.availableThemes, request.languages, request.config)
            .switchIfEmpty(Mono.error(SiteNotFoundException(id)))
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

    fun delete(id: Long, userId: Long): Mono<Void> =
        repo.findById(id, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(id)))
            .flatMap { repo.delete(id, userId) }
}
