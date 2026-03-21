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
    private val service = SiteService(repo)

    private val site = Site(
        id = 1L,
        userId = 1L,
        name = "My Blog",
        domain = "blog.example.com",
        description = null,
        stylesUrl = null,
        availableThemes = listOf(Theme.LIGHT),
        languages = listOf(Languages.ENGLISH),
        config = SiteConfig(),
        createdAt = OffsetDateTime.now(ZoneOffset.UTC),
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
    )

    private val createRequest = CreateSiteRequest(name = "My Blog", domain = "blog.example.com")

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

    @Test
    fun `update returns updated site when found`() {
        val updateRequest = UpdateSiteRequest(name = "Updated Blog")
        every { repo.update(1L, 1L, "Updated Blog", null, null, null, null, null) } returns Mono.just(site)

        StepVerifier.create(service.update(1L, 1L, updateRequest))
            .expectNext(site)
            .verifyComplete()
    }

    @Test
    fun `update throws SiteNotFoundException when site does not exist`() {
        val updateRequest = UpdateSiteRequest(name = "Updated Blog")
        every { repo.update(99L, 1L, "Updated Blog", null, null, null, null, null) } returns Mono.empty()

        StepVerifier.create(service.update(99L, 1L, updateRequest))
            .expectError(SiteNotFoundException::class.java)
            .verify()
    }

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
