package com.gonzalinux.domain.site

import com.gonzalinux.common.ForbiddenException
import com.gonzalinux.common.ServiceAccountNotFoundException
import com.gonzalinux.common.SiteNotFoundException
import com.gonzalinux.config.SubdomainProperties
import com.gonzalinux.domain.Languages
import com.gonzalinux.domain.user.EmailClient
import com.gonzalinux.domain.user.ServiceAccountRepository
import com.gonzalinux.domain.user.TokenService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Suppress("ReactiveStreamsUnusedPublisher")
class SiteInvitationServiceTest {

    private val repo = mockk<SiteInvitationRepository>(relaxed = true)
    private val siteRepo = mockk<SiteRepository>(relaxed = true)
    private val serviceAccountRepo = mockk<ServiceAccountRepository>(relaxed = true)
    private val tokenService = mockk<TokenService>(relaxed = true)
    private val emailClient = mockk<EmailClient>(relaxed = true)
    private val subdomainProperties = mockk<SubdomainProperties>(relaxed = true)
    private val service = SiteInvitationService(repo, siteRepo, serviceAccountRepo, tokenService, emailClient, subdomainProperties)

    private val site = Site(
        id = 1L,
        userId = 1L,
        name = "My Blog",
        domain = "blog.example.com",
        prefix = "",
        description = null,
        stylesUrl = null,
        availableThemes = listOf(Theme.LIGHT),
        languages = listOf(Languages.ENGLISH),
        config = SiteConfig(),
        status = SiteStatus.NOT_VERIFIED,
        createdAt = OffsetDateTime.now(ZoneOffset.UTC),
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
        verifyDate = OffsetDateTime.now(ZoneOffset.UTC),
        role = Roles.ADMIN
    )

    // --- inviteServiceAccount ---

    @Test
    fun `inviteServiceAccount adds the membership when caller is admin and owns the service account`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { serviceAccountRepo.existsByIdAndOwnerId(10L, 1L) } returns Mono.just(true)
        every { siteRepo.addMember(1L, 10L, Roles.WRITER) } returns Mono.just(true)

        StepVerifier.create(service.inviteServiceAccount(1L, 1L, 10L, Roles.WRITER))
            .verifyComplete()

        verify(exactly = 1) { siteRepo.addMember(1L, 10L, Roles.WRITER) }
    }

    @Test
    fun `inviteServiceAccount throws SiteNotFoundException when caller has no membership`() {
        every { siteRepo.findById(1L, 2L) } returns Mono.empty()

        StepVerifier.create(service.inviteServiceAccount(1L, 2L, 10L, Roles.WRITER))
            .expectError(SiteNotFoundException::class.java)
            .verify()

        verify(exactly = 0) { serviceAccountRepo.existsByIdAndOwnerId(any(), any()) }
    }

    @Test
    fun `inviteServiceAccount throws ForbiddenException when caller is not an admin`() {
        every { siteRepo.findById(1L, 3L) } returns Mono.just(site.copy(role = Roles.WRITER))

        StepVerifier.create(service.inviteServiceAccount(1L, 3L, 10L, Roles.WRITER))
            .expectError(ForbiddenException::class.java)
            .verify()

        verify(exactly = 0) { serviceAccountRepo.existsByIdAndOwnerId(any(), any()) }
    }

    @Test
    fun `inviteServiceAccount throws ServiceAccountNotFoundException when caller does not own the service account`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { serviceAccountRepo.existsByIdAndOwnerId(10L, 1L) } returns Mono.just(false)

        StepVerifier.create(service.inviteServiceAccount(1L, 1L, 10L, Roles.WRITER))
            .expectError(ServiceAccountNotFoundException::class.java)
            .verify()

        verify(exactly = 0) { siteRepo.addMember(any(), any(), any()) }
    }
}
