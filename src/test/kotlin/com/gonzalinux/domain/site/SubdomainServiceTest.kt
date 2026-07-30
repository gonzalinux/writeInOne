package com.gonzalinux.domain.site

import com.gonzalinux.common.BadRequestException
import com.gonzalinux.common.SiteDomainTakenException
import com.gonzalinux.common.SubdomainHeldException
import com.gonzalinux.common.SubdomainNotAllowedException
import com.gonzalinux.config.SubdomainProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SubdomainServiceTest {

    private val props = SubdomainProperties(
        baseDomain = "writeinone.com",
        minLength = 3,
        maxLength = 30,
        reservationDays = 7,
        reserved = setOf("www", "blog", "admin")
    )
    private val reservations = mockk<SubdomainReservationRepository>()
    private val siteRepo = mockk<SiteRepository>()
    private val service = SubdomainService(props, reservations, siteRepo)

    private fun reservation(userId: Long, daysAgo: Long) = SubdomainReservation(
        label = "gonzalo",
        userId = userId,
        releasedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(daysAgo)
    )

    // ── domain classification ──────────────────────────────────────────────

    @Test
    fun `isManaged is true only under the base domain`() {
        assertTrue(service.isManaged("gonzalo.writeinone.com"))
        assertFalse(service.isManaged("writeinone.com"))
        assertFalse(service.isManaged("gonzalo-leon.site"))
        assertFalse(service.isManaged("notwriteinone.com"))
    }

    @Test
    fun `isHomeDomain covers the bare domain, www and localhost with a port`() {
        assertTrue(service.isHomeDomain("writeinone.com"))
        assertTrue(service.isHomeDomain("www.writeinone.com"))
        assertTrue(service.isHomeDomain("localhost:8080"))
        assertFalse(service.isHomeDomain("gonzalo.writeinone.com"))
    }

    @Test
    fun `normalizeDomain lowercases and drops the trailing dot but keeps the port`() {
        assertEquals("blog.example.com", service.normalizeDomain(" Blog.Example.COM. "))
        assertEquals("localhost:8080", service.normalizeDomain("localhost:8080"))
    }

    // ── label validation ───────────────────────────────────────────────────

    @Test
    fun `requireLabel accepts a well formed single label`() {
        assertEquals("gonzalo", service.requireLabel("gonzalo.writeinone.com"))
        assertEquals("my-blog-2", service.requireLabel("my-blog-2.writeinone.com"))
    }

    @Test
    fun `requireLabel rejects a nested label`() {
        assertThrows<BadRequestException> { service.requireLabel("a.b.writeinone.com") }
    }

    @Test
    fun `requireLabel rejects labels outside the length bounds`() {
        assertThrows<BadRequestException> { service.requireLabel("ab.writeinone.com") }
        assertThrows<BadRequestException> { service.requireLabel("${"a".repeat(31)}.writeinone.com") }
    }

    @Test
    fun `requireLabel rejects leading and trailing hyphens`() {
        assertThrows<BadRequestException> { service.requireLabel("-blogg.writeinone.com") }
        assertThrows<BadRequestException> { service.requireLabel("blogg-.writeinone.com") }
    }

    @Test
    fun `requireLabel rejects punycode`() {
        assertThrows<BadRequestException> { service.requireLabel("xn--caf-dma.writeinone.com") }
    }

    /** The check is on the value, so a reserved label cannot be smuggled through any field. */
    @Test
    fun `requireLabel rejects reserved labels`() {
        assertThrows<SubdomainNotAllowedException> { service.requireLabel("admin.writeinone.com") }
        assertThrows<SubdomainNotAllowedException> { service.requireLabel("WWW.writeinone.com") }
    }

    @Test
    fun `labelOf returns null for domains we do not own`() {
        assertNull(props.labelOf("gonzalo-leon.site"))
    }

    // ── availability ───────────────────────────────────────────────────────

    @Test
    fun `ensureAvailable passes when nothing owns or holds the label`() {
        every { siteRepo.existsByDomain("gonzalo.writeinone.com") } returns Mono.just(false)
        every { reservations.findActive("gonzalo", 7) } returns Mono.empty()

        StepVerifier.create(service.ensureAvailable("gonzalo", 1L))
            .expectNext("gonzalo")
            .verifyComplete()
    }

    @Test
    fun `ensureAvailable fails when a live site owns the label`() {
        every { siteRepo.existsByDomain("gonzalo.writeinone.com") } returns Mono.just(true)

        StepVerifier.create(service.ensureAvailable("gonzalo", 1L))
            .expectError(SiteDomainTakenException::class.java)
            .verify()
    }

    @Test
    fun `ensureAvailable fails when another user still holds the reservation`() {
        every { siteRepo.existsByDomain("gonzalo.writeinone.com") } returns Mono.just(false)
        every { reservations.findActive("gonzalo", 7) } returns Mono.just(reservation(userId = 2L, daysAgo = 1))

        StepVerifier.create(service.ensureAvailable("gonzalo", 1L))
            .expectError(SubdomainHeldException::class.java)
            .verify()
    }

    /** The whole point of the hold: the previous owner can come back within the window. */
    @Test
    fun `ensureAvailable passes when the reservation belongs to the same user`() {
        every { siteRepo.existsByDomain("gonzalo.writeinone.com") } returns Mono.just(false)
        every { reservations.findActive("gonzalo", 7) } returns Mono.just(reservation(userId = 1L, daysAgo = 3))

        StepVerifier.create(service.ensureAvailable("gonzalo", 1L))
            .expectNext("gonzalo")
            .verifyComplete()
    }

    // ── claim / park ───────────────────────────────────────────────────────

    @Test
    fun `claim consumes the reservation on the label`() {
        every { reservations.release("gonzalo") } returns Mono.empty()

        StepVerifier.create(service.claim("gonzalo")).verifyComplete()

        verify { reservations.release("gonzalo") }
    }

    @Test
    fun `park reserves the label of a managed domain`() {
        every { reservations.reserve("gonzalo", 1L) } returns Mono.empty()

        StepVerifier.create(service.park("gonzalo.writeinone.com", 1L)).verifyComplete()

        verify { reservations.reserve("gonzalo", 1L) }
    }

    @Test
    fun `park is a no-op for a domain we do not own`() {
        StepVerifier.create(service.park("gonzalo-leon.site", 1L)).verifyComplete()

        verify(exactly = 0) { reservations.reserve(any(), any()) }
    }

    // ── check ──────────────────────────────────────────────────────────────

    @Test
    fun `check reports a free label as available`() {
        every { siteRepo.existsByDomain("gonzalo.writeinone.com") } returns Mono.just(false)
        every { reservations.findActive("gonzalo", 7) } returns Mono.empty()

        StepVerifier.create(service.check("Gonzalo", 1L))
            .assertNext {
                assertEquals(true, it.available)
                assertEquals("gonzalo", it.name)
                assertEquals("gonzalo.writeinone.com", it.domain)
                assertNull(it.reason)
            }
            .verifyComplete()
    }

    /** The form needs a verdict with a reason, not an error response. */
    @Test
    fun `check reports a reserved label as unavailable with a reason`() {
        StepVerifier.create(service.check("admin", 1L))
            .assertNext {
                assertEquals(false, it.available)
                assertTrue(it.reason!!.contains("reserved"))
            }
            .verifyComplete()
    }

    @Test
    fun `check reports a malformed label as unavailable`() {
        StepVerifier.create(service.check("ab", 1L))
            .assertNext { assertEquals(false, it.available) }
            .verifyComplete()
    }
}
