package com.gonzalinux.domain.site

import com.gonzalinux.common.InvalidInvitationException
import com.gonzalinux.common.InvitationNotFoundException
import com.gonzalinux.common.ServiceAccountNotFoundException
import com.gonzalinux.common.SiteNotFoundException
import com.gonzalinux.config.SubdomainProperties
import com.gonzalinux.domain.user.EmailClient
import com.gonzalinux.domain.user.ServiceAccountRepository
import com.gonzalinux.domain.user.TokenService
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

private val logger = KotlinLogging.logger {}

/** Long enough to survive a weekend, short enough that a leaked link stops mattering. */
private const val EXPIRY_HOURS = 48L

@Service
class SiteInvitationService(
    private val repo: SiteInvitationRepository,
    private val siteRepo: SiteRepository,
    private val serviceAccountRepo: ServiceAccountRepository,
    private val tokenService: TokenService,
    private val emailClient: EmailClient,
    private val subdomainProperties: SubdomainProperties
) {

    /** [email] null means the caller wants the link back to share themselves; nothing is sent. */
    fun create(siteId: Long, userId: Long, role: Roles, email: String?): Mono<CreatedInvitation> =
        requireAdminOn(siteId, userId)
            .flatMap { site ->
                val token = newToken()
                val expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(EXPIRY_HOURS)
                repo.create(siteId, role, tokenService.hashToken(token), expiresAt)
                    .flatMap { invitation ->
                        val acceptUrl = acceptUrl(token)
                        // The link is in the response either way, so a mail outage costs the admin
                        // a copy-paste rather than the whole invitation.
                        deliver(email, site.name, acceptUrl)
                            .thenReturn(CreatedInvitation(invitation, token, acceptUrl))
                    }
            }

    /**
     * Skips the token/email round-trip entirely — the row goes straight into `site_members`.
     * Constraint enforced here, not at the DB: [serviceAccountId] must be owned by [userId], the
     * same admin who must hold ADMIN on [siteId] — see Collaboration.md.
     */
    fun inviteServiceAccount(siteId: Long, userId: Long, serviceAccountId: Long, role: Roles): Mono<Unit> =
        requireAdminOn(siteId, userId)
            .flatMap {
                serviceAccountRepo.existsByIdAndOwnerId(serviceAccountId, userId)
                    .flatMap { owned ->
                        if (!owned) Mono.error(ServiceAccountNotFoundException(serviceAccountId))
                        else siteRepo.addMember(siteId, serviceAccountId, role).map { Unit }
                    }
            }

    fun list(siteId: Long, userId: Long): Flux<SiteInvitation> =
        requireAdminOn(siteId, userId)
            .flatMapMany { repo.findActiveBySiteId(siteId) }

    fun revoke(siteId: Long, invitationId: Long, userId: Long): Mono<Unit> =
        requireAdminOn(siteId, userId)
            .flatMap { repo.delete(siteId, invitationId) }
            .flatMap { rows ->
                if (rows == 0L) Mono.error(InvitationNotFoundException(invitationId))
                else Mono.just(Unit)
            }

    /**
     * Deliberately not guarded by site membership: the invitee has none yet, and the token *is*
     * the authorization. Any authenticated user may call this — with a valid token.
     */
    fun accept(userId: Long, token: String): Mono<SiteInvitation> =
        repo.findByTokenHash(tokenService.hashToken(token))
            .switchIfEmpty(Mono.error(InvalidInvitationException("This invitation link is not valid.")))
            .flatMap { invitation ->
                if (invitation.expiresAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC)))
                    Mono.error(InvalidInvitationException("This invitation has expired."))
                else
                // Already a member: succeed without touching the existing role, so following the
                // link twice — or following an old one — can never downgrade someone.
                    siteRepo.findById(invitation.siteId, userId)
                        .map { invitation }
                        .switchIfEmpty(redeem(invitation, userId))
            }

    private fun redeem(invitation: SiteInvitation, userId: Long): Mono<SiteInvitation> =
        repo.redeem(invitation.id, userId)
            .flatMap { granted ->
                if (granted) invitation.toMono()
                else Mono.error(InvalidInvitationException("This invitation is no longer valid."))
            }

    private fun requireAdminOn(siteId: Long, userId: Long): Mono<Site> =
        siteRepo.findById(siteId, userId)
            .requireAdmin()
            // After the guard, so a non-member gets 404 and a member with the wrong role gets 403.
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))

    private fun deliver(email: String?, siteName: String, acceptUrl: String): Mono<Void> {
        val to = email?.trim()?.takeIf { it.isNotEmpty() } ?: return Mono.empty()
        return emailClient.sendInvitationEmail(to, siteName, acceptUrl)
            .onErrorResume { e ->
                logger.warn(e) { "Failed to send invitation email for site '$siteName'" }
                Mono.empty()
            }
    }

    private fun newToken(): String =
        UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")

    private fun acceptUrl(token: String): String =
        "https://${subdomainProperties.baseDomain}/admin/invitations/accept?token=$token"
}
