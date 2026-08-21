package com.gonzalinux.domain.tag

import com.gonzalinux.common.SiteNotFoundException
import com.gonzalinux.domain.site.SiteRepository
import com.gonzalinux.domain.site.requirePublish
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class TagService(private val tagRepo: TagRepository, private val siteRepo: SiteRepository) {

    fun list(siteId: Long, userId: Long, search: String? = null): Flux<Tag> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMapMany { tagRepo.findBySiteId(siteId, search, limit = 20) }

    fun delete(id: Long, siteId: Long, userId: Long): Mono<Void> =
        siteRepo.findById(siteId, userId)
            .requirePublish()
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap { tagRepo.delete(id, siteId) }
}
