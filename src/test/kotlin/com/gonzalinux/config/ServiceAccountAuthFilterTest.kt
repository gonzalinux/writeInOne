package com.gonzalinux.config

import com.gonzalinux.common.RequestContextHolder.getUserId
import com.gonzalinux.common.UnauthorizedException
import com.gonzalinux.domain.user.TokenService
import com.gonzalinux.domain.user.User
import com.gonzalinux.domain.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Suppress("ReactiveStreamsUnusedPublisher")
class ServiceAccountAuthFilterTest {

    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val tokenService = mockk<TokenService>(relaxed = true)
    private val jwtAuthFilter = mockk<JwtAuthFilter>(relaxed = true)
    private val filter = ServiceAccountAuthFilter(userRepository, tokenService, jwtAuthFilter)

    private val request = mockk<ServerRequest>()
    private val headers = mockk<ServerRequest.Headers>()
    private val response = mockk<ServerResponse>()

    private val now = OffsetDateTime.now(ZoneOffset.UTC)
    private val serviceAccount = User(
        id = 42L,
        email = "sa-abc@service.internal",
        passwordHash = null,
        displayName = "Claude Desktop",
        emailVerified = true,
        ownerId = 1L,
        serviceAccountTokenHash = "hashed-token",
        createdAt = now,
        updatedAt = now
    )

    init {
        every { request.headers() } returns headers
    }

    /** Reads back whatever userId the filter wrote into the Reactor context around `next`. */
    private fun capturingNext(): Pair<HandlerFunction<ServerResponse>, () -> Long?> {
        var captured: Long? = null
        val next = HandlerFunction<ServerResponse> {
            Mono.deferContextual { ctx ->
                captured = ctx.getUserId()
                Mono.just(response)
            }
        }
        return next to { captured }
    }

    @Test
    fun `delegates to JwtAuthFilter when no Authorization header is present`() {
        every { headers.firstHeader("Authorization") } returns null
        val (next, _) = capturingNext()
        every { jwtAuthFilter.filter(request, next) } returns Mono.just(response)

        StepVerifier.create(filter.filter(request, next))
            .expectNext(response)
            .verifyComplete()

        verify { jwtAuthFilter.filter(request, next) }
        verify(exactly = 0) { userRepository.findByServiceAccountTokenHash(any()) }
    }

    @Test
    fun `delegates to JwtAuthFilter when the Authorization header is not a Bearer token`() {
        every { headers.firstHeader("Authorization") } returns "Basic dXNlcjpwYXNz"
        val (next, _) = capturingNext()
        every { jwtAuthFilter.filter(request, next) } returns Mono.just(response)

        StepVerifier.create(filter.filter(request, next))
            .expectNext(response)
            .verifyComplete()

        verify { jwtAuthFilter.filter(request, next) }
    }

    @Test
    fun `authenticates a valid bearer token and writes the service account's userId into context`() {
        every { headers.firstHeader("Authorization") } returns "Bearer raw-token"
        every { tokenService.hashToken("raw-token") } returns "hashed-token"
        every { userRepository.findByServiceAccountTokenHash("hashed-token") } returns Mono.just(serviceAccount)
        val (next, capturedUserId) = capturingNext()

        StepVerifier.create(filter.filter(request, next))
            .expectNext(response)
            .verifyComplete()

        assert(capturedUserId() == 42L) { "expected userId 42, got ${capturedUserId()}" }
        verify(exactly = 0) { jwtAuthFilter.filter(any(), any()) }
    }

    @Test
    fun `throws UnauthorizedException for an unknown bearer token`() {
        every { headers.firstHeader("Authorization") } returns "Bearer bogus-token"
        every { tokenService.hashToken("bogus-token") } returns "hashed-bogus"
        every { userRepository.findByServiceAccountTokenHash("hashed-bogus") } returns Mono.empty()
        val (next, _) = capturingNext()

        StepVerifier.create(filter.filter(request, next))
            .expectError(UnauthorizedException::class.java)
            .verify()
    }
}
