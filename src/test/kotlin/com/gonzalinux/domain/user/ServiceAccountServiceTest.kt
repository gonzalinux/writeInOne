package com.gonzalinux.domain.user

import com.gonzalinux.common.ServiceAccountNotFoundException
import com.gonzalinux.domain.Languages
import com.gonzalinux.domain.site.Roles
import com.gonzalinux.domain.site.Site
import com.gonzalinux.domain.site.SiteConfig
import com.gonzalinux.domain.site.SiteRepository
import com.gonzalinux.domain.site.SiteStatus
import com.gonzalinux.domain.site.Theme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Suppress("ReactiveStreamsUnusedPublisher")
class ServiceAccountServiceTest {

    private val repo = mockk<ServiceAccountRepository>(relaxed = true)
    private val siteRepo = mockk<SiteRepository>(relaxed = true)
    private val tokenService = mockk<TokenService>(relaxed = true)
    private val service = ServiceAccountService(repo, siteRepo, tokenService)

    private val now = OffsetDateTime.now(ZoneOffset.UTC)

    private val site = Site(
        id = 5L,
        userId = 1L,
        name = "SA Test Blog",
        domain = "sa-test.example.com",
        prefix = "",
        description = null,
        stylesUrl = null,
        availableThemes = listOf(Theme.LIGHT),
        languages = listOf(Languages.ENGLISH),
        config = SiteConfig(),
        status = SiteStatus.VERIFIED,
        createdAt = now,
        updatedAt = now,
        verifyDate = now,
        role = Roles.WRITER
    )

    private val account = User(
        id = 10L,
        email = "sa-abc@service.internal",
        passwordHash = null,
        displayName = "Claude Desktop",
        emailVerified = true,
        ownerId = 1L,
        serviceAccountTokenHash = "hashed-token",
        createdAt = now,
        updatedAt = now
    )

    // --- create ---

    @Test
    fun `create hashes a freshly generated token and returns the raw token alongside the account`() {
        val hashedInputs = mutableListOf<String>()
        every { tokenService.hashToken(capture(hashedInputs)) } returns "hashed-token"
        every { repo.create(1L, "Claude Desktop", "hashed-token") } returns Mono.just(account)

        StepVerifier.create(service.create(1L, "Claude Desktop"))
            .assertNext { created ->
                assertEquals(account, created.account)
                // The token handed back is the same raw value that was hashed before storage.
                assertEquals(hashedInputs.single(), created.token)
                assertNotEquals(created.token, account.serviceAccountTokenHash)
            }
            .verifyComplete()

        verify { repo.create(1L, "Claude Desktop", "hashed-token") }
    }

    // --- list ---

    @Test
    fun `list returns all service accounts for the owner`() {
        every { repo.findAllByOwnerId(1L) } returns Flux.just(account)

        StepVerifier.create(service.list(1L))
            .expectNext(account)
            .verifyComplete()
    }

    // --- listSites ---

    @Test
    fun `listSites returns the account's sites when owned by the caller`() {
        every { repo.existsByIdAndOwnerId(10L, 1L) } returns Mono.just(true)
        every { siteRepo.findAllByUserId(10L) } returns Flux.just(site)

        StepVerifier.create(service.listSites(10L, 1L))
            .expectNext(site)
            .verifyComplete()
    }

    @Test
    fun `listSites throws ServiceAccountNotFoundException when not owned by the caller`() {
        every { repo.existsByIdAndOwnerId(10L, 2L) } returns Mono.just(false)

        StepVerifier.create(service.listSites(10L, 2L))
            .expectError(ServiceAccountNotFoundException::class.java)
            .verify()

        verify(exactly = 0) { siteRepo.findAllByUserId(any()) }
    }

    // --- revoke ---

    @Test
    fun `revoke deletes the account when owned by the caller`() {
        every { repo.deleteByIdAndOwnerId(10L, 1L) } returns Mono.just(1L)

        StepVerifier.create(service.revoke(10L, 1L))
            .verifyComplete()
    }

    @Test
    fun `revoke throws ServiceAccountNotFoundException when not owned by the caller`() {
        every { repo.deleteByIdAndOwnerId(10L, 2L) } returns Mono.just(0L)

        StepVerifier.create(service.revoke(10L, 2L))
            .expectError(ServiceAccountNotFoundException::class.java)
            .verify()
    }

    // --- rotate ---

    @Test
    fun `rotate stores a new token hash and returns the new raw token`() {
        every { tokenService.hashToken(any()) } returns "new-hashed-token"
        every { repo.updateTokenHash(10L, 1L, "new-hashed-token") } returns Mono.just(1L)

        StepVerifier.create(service.rotate(10L, 1L))
            .expectNextCount(1)
            .verifyComplete()

        verify { repo.updateTokenHash(10L, 1L, "new-hashed-token") }
    }

    @Test
    fun `rotate throws ServiceAccountNotFoundException when not owned by the caller`() {
        every { tokenService.hashToken(any()) } returns "new-hashed-token"
        every { repo.updateTokenHash(10L, 2L, "new-hashed-token") } returns Mono.just(0L)

        StepVerifier.create(service.rotate(10L, 2L))
            .expectError(ServiceAccountNotFoundException::class.java)
            .verify()
    }
}
