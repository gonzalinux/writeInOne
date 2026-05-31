package com.gonzalinux.domain.owner

import com.gonzalinux.domain.site.Site
import com.gonzalinux.domain.site.SiteRepository
import com.gonzalinux.domain.site.SiteStatus
import com.gonzalinux.domain.user.User
import com.gonzalinux.domain.user.UserRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

data class OwnerStats(
    val userCount: Long,
    val siteCount: Long,
    val recentUsers: List<User>,
    val recentSites: List<Site>
)

@Service
class OwnerService(
    private val userRepository: UserRepository,
    private val siteRepository: SiteRepository
) {

    fun getStats(): Mono<OwnerStats> =
        Mono.zip(
            userRepository.countAll(),
            siteRepository.countAll(),
            userRepository.findRecent(50).collectList(),
            siteRepository.findRecentSites(50).collectList()
        ).map { OwnerStats(it.t1, it.t2, it.t3, it.t4) }

    fun verifySite(siteId: Long): Mono<Void> =
        siteRepository.updateStatus(siteId, SiteStatus.VERIFIED).then()
}
