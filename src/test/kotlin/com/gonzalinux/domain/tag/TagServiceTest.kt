package com.gonzalinux.domain.tag

import com.gonzalinux.common.SiteNotFoundException
import com.gonzalinux.domain.Languages
import com.gonzalinux.domain.site.Site
import com.gonzalinux.domain.site.SiteConfig
import com.gonzalinux.domain.site.SiteRepository
import com.gonzalinux.domain.site.SiteStatus
import com.gonzalinux.domain.site.Theme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TagServiceTest {

    private val tagRepo = mockk<TagRepository>()
    private val siteRepo = mockk<SiteRepository>()
    private val service = TagService(tagRepo, siteRepo)

    private val site = Site(
        id = 1L, userId = 1L, name = "My Blog", domain = "blog.example.com",
        prefix = "", description = null, stylesUrl = null, availableThemes = listOf(Theme.LIGHT),
        languages = listOf(Languages.ENGLISH), config = SiteConfig(), status = SiteStatus.NOT_VERIFIED,
        createdAt = OffsetDateTime.now(ZoneOffset.UTC), updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
        verifyDate = OffsetDateTime.now(ZoneOffset.UTC)
    )

    private val tag = Tag(id = 1L, siteId = 1L, name = "kotlin", createdAt = OffsetDateTime.now(ZoneOffset.UTC))

    @Test
    fun `list returns tags for site when site exists`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { tagRepo.findBySiteId(1L) } returns Flux.just(tag)

        StepVerifier.create(service.list(1L, 1L))
            .expectNext(tag)
            .verifyComplete()
    }

    @Test
    fun `list throws SiteNotFoundException when site does not exist`() {
        every { siteRepo.findById(99L, 1L) } returns Mono.empty()

        StepVerifier.create(service.list(99L, 1L))
            .expectError(SiteNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `delete removes tag when site exists`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { tagRepo.delete(1L, 1L) } returns Mono.empty()

        StepVerifier.create(service.delete(1L, 1L, 1L))
            .verifyComplete()

        verify { tagRepo.delete(1L, 1L) }
    }

    @Test
    fun `delete throws SiteNotFoundException when site does not exist`() {
        every { siteRepo.findById(99L, 1L) } returns Mono.empty()

        StepVerifier.create(service.delete(1L, 99L, 1L))
            .expectError(SiteNotFoundException::class.java)
            .verify()
    }
}
