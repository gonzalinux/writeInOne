package com.gonzalinux.domain.site

import com.gonzalinux.common.ApiException
import com.gonzalinux.common.BadRequestException
import com.gonzalinux.common.SiteDomainTakenException
import com.gonzalinux.common.SubdomainHeldException
import com.gonzalinux.common.SubdomainNotAllowedException
import com.gonzalinux.config.SubdomainProperties
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/** What the site form needs to render the subdomain field, plus the verdict for a candidate. */
data class SubdomainInfo(
    val baseDomain: String,
    val minLength: Int,
    val maxLength: Int,
    val name: String? = null,
    val domain: String? = null,
    val available: Boolean? = null,
    val reason: String? = null
)

/**
 * Subdomains of our own base domain are covered by the wildcard certificate and by DNS we
 * control, so they need no ownership proof — unlike a user's own domain, which has to be
 * proven reachable by [VerifyClient] before it goes live.
 */
@Service
class SubdomainService(
    private val props: SubdomainProperties,
    private val reservations: SubdomainReservationRepository,
    private val siteRepo: SiteRepository
) {
    private val labelPattern = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")

    fun isManaged(domain: String): Boolean = props.isManaged(domain)

    fun isHomeDomain(domain: String): Boolean = props.isHomeDomain(domain)

    fun domainFor(label: String): String = props.domainFor(label)

    /** Site domains are stored lowercase so `HostFilter` can match the incoming header directly. */
    fun normalizeDomain(domain: String): String = props.normalizeHost(domain)

    fun info(): SubdomainInfo = SubdomainInfo(props.baseDomain, props.minLength, props.maxLength)

    /**
     * Validates the shape of a domain under our base domain and returns its label.
     * Applied to the value, not to the form the user filled in, so a reserved label
     * cannot be smuggled through the custom-domain field.
     */
    fun requireLabel(domain: String): String {
        val label = props.labelOf(domain)
            ?: throw BadRequestException(
                "'$domain' must be a single label directly under ${props.baseDomain} (e.g. myblog.${props.baseDomain})"
            )
        if (label.length < props.minLength || label.length > props.maxLength)
            throw BadRequestException("Subdomain must be between ${props.minLength} and ${props.maxLength} characters")
        if (!labelPattern.matches(label))
            throw BadRequestException("Subdomain may only contain lowercase letters, digits and inner hyphens")
        if (label.startsWith("xn--"))
            throw BadRequestException("Punycode subdomains are not allowed")
        if (label in props.reservedLabels)
            throw SubdomainNotAllowedException(label)
        return label
    }

    /** Fails when a live site owns the label, or another user still holds it after a release. */
    fun ensureAvailable(label: String, userId: Long): Mono<String> =
        siteRepo.existsByDomain(props.domainFor(label))
            .flatMap { taken ->
                if (taken) Mono.error(SiteDomainTakenException(props.domainFor(label)))
                else reservations.findActive(label, props.reservationDays)
                    .flatMap { held ->
                        if (held.userId == userId) Mono.empty()
                        else Mono.error(
                            SubdomainHeldException(
                                label,
                                held.releasedAt.plusDays(props.reservationDays).toLocalDate()
                            )
                        )
                    }
                    .thenReturn(label)
            }

    /** Taking a label consumes the reservation the user was holding on it, if any. */
    fun claim(label: String): Mono<Void> = reservations.release(label)

    /** Parks a released label so its previous owner can come back to it within the window. */
    fun park(domain: String, userId: Long): Mono<Void> =
        props.labelOf(domain)?.let { reservations.reserve(it, userId) } ?: Mono.empty()

    fun check(name: String, userId: Long): Mono<SubdomainInfo> {
        val label = props.normalizeHost(name)
        val base = info().copy(name = label, domain = props.domainFor(label))
        return Mono.fromCallable { requireLabel(props.domainFor(label)) }
            .flatMap { ensureAvailable(it, userId) }
            .map { base.copy(available = true) }
            .onErrorResume(ApiException::class.java) { e ->
                Mono.just(base.copy(available = false, reason = e.details))
            }
    }

    fun purgeExpired(): Mono<Long> = reservations.deleteExpired(props.reservationDays)
}
