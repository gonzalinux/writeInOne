package com.gonzalinux.domain.site

import com.gonzalinux.api.data.CreateSiteRequest
import com.gonzalinux.api.data.UpdateSiteRequest
import com.gonzalinux.common.BadRequestException
import com.gonzalinux.common.SiteDomainTakenException
import com.gonzalinux.common.SiteNotFoundException
import com.gonzalinux.domain.Languages
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SiteServiceTest {

    private val repo = mockk<SiteRepository>()
    private val verifyClient = mockk<VerifyClient>()
    private val service = SiteService(repo, verifyClient)

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
        verifyDate = OffsetDateTime.now(ZoneOffset.UTC)
    )

    private val createRequest = CreateSiteRequest(name = "My Blog", domain = "blog.example.com")

    // ── create ─────────────────────────────────────────────────────────────

    @Test
    fun `create returns site when domain is not taken`() {
        every { repo.existsByDomain("blog.example.com") } returns Mono.just(false)
        every {
            repo.create(1L, "My Blog", "blog.example.com", null, null, listOf(Theme.LIGHT), listOf(Languages.ENGLISH), SiteConfig())
        } returns Mono.just(site)

        StepVerifier.create(service.create(1L, createRequest))
            .expectNext(site)
            .verifyComplete()
    }

    @Test
    fun `create throws SiteDomainTakenException when domain is already taken`() {
        every { repo.existsByDomain("blog.example.com") } returns Mono.just(true)

        StepVerifier.create(service.create(1L, createRequest))
            .expectError(SiteDomainTakenException::class.java)
            .verify()
    }

    // ── list / findById ────────────────────────────────────────────────────

    @Test
    fun `list returns all sites for user`() {
        every { repo.findAllByUserId(1L) } returns Flux.just(site)

        StepVerifier.create(service.list(1L))
            .expectNext(site)
            .verifyComplete()
    }

    @Test
    fun `list returns empty when user has no sites`() {
        every { repo.findAllByUserId(1L) } returns Flux.empty()

        StepVerifier.create(service.list(1L))
            .verifyComplete()
    }

    @Test
    fun `findById returns site when found`() {
        every { repo.findById(1L, 1L) } returns Mono.just(site)

        StepVerifier.create(service.findById(1L, 1L))
            .expectNext(site)
            .verifyComplete()
    }

    @Test
    fun `findById throws SiteNotFoundException when site does not exist`() {
        every { repo.findById(99L, 1L) } returns Mono.empty()

        StepVerifier.create(service.findById(99L, 1L))
            .expectError(SiteNotFoundException::class.java)
            .verify()
    }

    // ── update ─────────────────────────────────────────────────────────────

    @Test
    fun `update returns updated site when found`() {
        val request = UpdateSiteRequest(name = "Updated Blog")
        every { repo.update(1L, 1L, "Updated Blog", null, null, null, null, null, null, null, null, null) } returns Mono.just(site)

        StepVerifier.create(service.update(1L, 1L, request))
            .expectNext(site)
            .verifyComplete()
    }

    @Test
    fun `update throws SiteNotFoundException when site does not exist`() {
        val request = UpdateSiteRequest(name = "Updated Blog")
        every { repo.update(99L, 1L, "Updated Blog", null, null, null, null, null, null, null, null, null) } returns Mono.empty()

        StepVerifier.create(service.update(99L, 1L, request))
            .expectError(SiteNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `update resets verification when domain changes`() {
        val request = UpdateSiteRequest(domain = "new.example.com")
        every { repo.findById(1L, 1L) } returns Mono.just(site)
        every { repo.existsByDomain("new.example.com") } returns Mono.just(false)
        every { repo.update(1L, 1L, null, "new.example.com", null, null, null, null, null, SiteStatus.NOT_VERIFIED, null, any()) } returns Mono.just(site)

        StepVerifier.create(service.update(1L, 1L, request))
            .expectNext(site)
            .verifyComplete()

        verify { repo.update(1L, 1L, any(), any(), any(), any(), any(), any(), any(), SiteStatus.NOT_VERIFIED, any(), any()) }
    }

    @Test
    fun `update does not reset verification when domain is unchanged`() {
        val request = UpdateSiteRequest(domain = "blog.example.com", name = "New Name")
        every { repo.findById(1L, 1L) } returns Mono.just(site)
        every { repo.update(1L, 1L, "New Name", "blog.example.com", null, null, null, null, null, null, null, null) } returns Mono.just(site)

        StepVerifier.create(service.update(1L, 1L, request))
            .expectNext(site)
            .verifyComplete()

        verify { repo.update(1L, 1L, any(), any(), any(), any(), any(), any(), any(), null, any(), null) }
    }

    @Test
    fun `update resets verification when requestVerification is true`() {
        val request = UpdateSiteRequest(name = "Same Name", requestVerification = true)
        every { repo.update(1L, 1L, "Same Name", null, null, null, null, null, null, SiteStatus.NOT_VERIFIED, null, any()) } returns Mono.just(site)

        StepVerifier.create(service.update(1L, 1L, request))
            .expectNext(site)
            .verifyComplete()

        verify { repo.update(1L, 1L, any(), any(), any(), any(), any(), any(), any(), SiteStatus.NOT_VERIFIED, any(), any()) }
    }

    @Test
    fun `update does not reset verification when requestVerification is false`() {
        val request = UpdateSiteRequest(name = "Same Name", requestVerification = false)
        every { repo.update(1L, 1L, "Same Name", null, null, null, null, null, null, null, null, null) } returns Mono.just(site)

        StepVerifier.create(service.update(1L, 1L, request))
            .expectNext(site)
            .verifyComplete()

        verify { repo.update(1L, 1L, any(), any(), any(), any(), any(), any(), any(), null, any(), null) }
    }

    @Test
    fun `update throws SiteDomainTakenException when new domain is already taken`() {
        val request = UpdateSiteRequest(domain = "taken.example.com")
        every { repo.findById(1L, 1L) } returns Mono.just(site)
        every { repo.existsByDomain("taken.example.com") } returns Mono.just(true)

        StepVerifier.create(service.update(1L, 1L, request))
            .expectError(SiteDomainTakenException::class.java)
            .verify()
    }

    // ── verifyPendingSites ─────────────────────────────────────────────────

    @Test
    fun `verifyPendingSites marks site as verified when token matches`() {
        every { repo.notVerified() } returns Flux.just(site)
        every { verifyClient.verify(site.domain, site.prefix) } returns Mono.just(true)
        every { repo.update(site.id, site.userId, status = SiteStatus.VERIFIED) } returns Mono.just(site)

        StepVerifier.create(service.verifyPendingSites())
            .verifyComplete()

        verify { repo.update(site.id, site.userId, status = SiteStatus.VERIFIED) }
    }

    @Test
    fun `verifyPendingSites does not update site when verification fails`() {
        every { repo.notVerified() } returns Flux.just(site)
        every { verifyClient.verify(site.domain, site.prefix) } returns Mono.just(false)

        StepVerifier.create(service.verifyPendingSites())
            .verifyComplete()

        verify(exactly = 0) { repo.update(any(), any(), status = SiteStatus.VERIFIED) }
    }

    @Test
    fun `verifyPendingSites continues to next site when one throws`() {
        val site2 = site.copy(id = 2L, domain = "other.example.com")
        every { repo.notVerified() } returns Flux.just(site, site2)
        every { verifyClient.verify(site.domain, site.prefix) } returns Mono.error(RuntimeException("Connection refused"))
        every { verifyClient.verify(site2.domain, site2.prefix) } returns Mono.just(true)
        every { repo.update(site2.id, site2.userId, status = SiteStatus.VERIFIED) } returns Mono.just(site2)

        StepVerifier.create(service.verifyPendingSites())
            .verifyComplete()

        verify(exactly = 0) { repo.update(site.id, any(), status = SiteStatus.VERIFIED) }
        verify { repo.update(site2.id, site2.userId, status = SiteStatus.VERIFIED) }
    }

    @Test
    fun `verifyPendingSites does nothing when no unverified sites`() {
        every { repo.notVerified() } returns Flux.empty()

        StepVerifier.create(service.verifyPendingSites())
            .verifyComplete()

        verify(exactly = 0) { verifyClient.verify(any(), any()) }
    }

    // ── delete ─────────────────────────────────────────────────────────────

    @Test
    fun `delete removes site when found`() {
        every { repo.findById(1L, 1L) } returns Mono.just(site)
        every { repo.delete(1L, 1L) } returns Mono.empty()

        StepVerifier.create(service.delete(1L, 1L))
            .verifyComplete()

        verify { repo.delete(1L, 1L) }
    }

    @Test
    fun `delete throws SiteNotFoundException when site does not exist`() {
        every { repo.findById(99L, 1L) } returns Mono.empty()

        StepVerifier.create(service.delete(99L, 1L))
            .expectError(SiteNotFoundException::class.java)
            .verify()
    }

    // ── nav link validation ────────────────────────────────────────────────

    @Test
    fun `create throws BadRequestException when nav link URL is javascript scheme`() {
        val config = SiteConfig(en = LangConfig(nav = listOf(NavLink("Evil", "javascript:alert(1)"))))
        val request = CreateSiteRequest(name = "Blog", domain = "blog.example.com", config = config)

        StepVerifier.create(service.create(1L, request))
            .expectError(BadRequestException::class.java)
            .verify()
    }

    @Test
    fun `create throws BadRequestException when nav link URL has no valid scheme`() {
        val config = SiteConfig(en = LangConfig(nav = listOf(NavLink("Bad", "data:text/html,<script>alert(1)</script>"))))
        val request = CreateSiteRequest(name = "Blog", domain = "blog.example.com", config = config)

        StepVerifier.create(service.create(1L, request))
            .expectError(BadRequestException::class.java)
            .verify()
    }

    @Test
    fun `create allows nav links with http, https and relative URLs`() {
        val config = SiteConfig(en = LangConfig(nav = listOf(
            NavLink("Home", "/"),
            NavLink("About", "/about"),
            NavLink("External", "https://example.com"),
            NavLink("External HTTP", "http://example.com")
        )))
        val request = CreateSiteRequest(name = "Blog", domain = "blog.example.com", config = config)

        every { repo.existsByDomain("blog.example.com") } returns Mono.just(false)
        every { repo.create(any(), any(), any(), any(), any(), any(), any(), any()) } returns Mono.just(site)

        StepVerifier.create(service.create(1L, request))
            .expectNext(site)
            .verifyComplete()
    }

    @Test
    fun `update throws BadRequestException when nav link URL is invalid`() {
        val config = SiteConfig(es = LangConfig(nav = listOf(NavLink("Mal", "javascript:void(0)"))))
        val request = UpdateSiteRequest(config = config)

        StepVerifier.create(service.update(1L, 1L, request))
            .expectError(BadRequestException::class.java)
            .verify()
    }
}
