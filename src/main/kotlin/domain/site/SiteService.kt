package com.gonzalinux.domain.site

import com.gonzalinux.api.data.CreateSiteRequest
import com.gonzalinux.api.data.UpdateSiteRequest
import com.gonzalinux.common.SiteDomainTakenException
import com.gonzalinux.common.SiteNotFoundException
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class SiteService(private val repo: SiteRepository) {

    fun create(userId: Long, request: CreateSiteRequest): Mono<Site> =
        repo.existsByDomain(request.domain)
            .flatMap { exists ->
                if (exists) Mono.error(SiteDomainTakenException(request.domain))
                else repo.create(userId, request.name, request.domain, request.description, request.stylesUrl, request.languages, request.config)
            }

    fun list(userId: Long): Flux<Site> =
        repo.findAllByUserId(userId)

    fun update(id: Long, userId: Long, request: UpdateSiteRequest): Mono<Site> =
        repo.update(id, userId, request.name, request.description, request.stylesUrl, request.languages, request.config)
            .switchIfEmpty(Mono.error(SiteNotFoundException(id)))

    fun delete(id: Long, userId: Long): Mono<Void> =
        repo.findById(id, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(id)))
            .flatMap { repo.delete(id, userId) }
}
