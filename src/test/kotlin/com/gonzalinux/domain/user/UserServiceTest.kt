package com.gonzalinux.domain.user

import com.gonzalinux.common.UnauthorizedException
import com.gonzalinux.common.UserAlreadyExistsException
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import com.gonzalinux.config.PasswordEncoder
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.time.ZoneOffset

class UserServiceTest {

    private val repo = mockk<UserRepository>(relaxed=true)
    private val encoder = mockk<PasswordEncoder>(relaxed=true)
    private val tokenService = mockk<TokenService>()
    private val registry = mockk<MeterRegistry>(relaxed = true)
    private val service = UserService(repo, encoder, tokenService, registry)

    private val user = User(
        id = 1L,
        email = "test@test.com",
        passwordHash = "hashed",
        displayName = "Test User",
        createdAt = OffsetDateTime.now(ZoneOffset.UTC),
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
    )

    private val accessToken = AccessToken("access-jwt")
    private val refreshToken = RefreshToken("refresh-value", OffsetDateTime.now(ZoneOffset.UTC).plusDays(30))
    private val authTokens = AuthTokens(accessToken, refreshToken)

    @Test
    fun `register creates user and returns tokens when email is new`() {
        every { repo.findByEmail("test@test.com") } returns Mono.empty()
        every { encoder.encode("password") } returns "hashed"
        every { repo.create("test@test.com", "Test User", "hashed") } returns Mono.just(user)
        every { tokenService.generateAccessToken(user.id) } returns accessToken
        every { tokenService.generateRefreshToken() } returns refreshToken
        every { tokenService.hashToken("refresh-value") } returns "hashed-refresh"
        every { repo.saveRefreshToken(1L, "hashed-refresh", refreshToken.expiresAt) } returns Mono.empty()

        StepVerifier.create(service.register("test@test.com", "Test User", "password"))
            .expectNext(authTokens)
            .verifyComplete()
    }

    @Test
    fun `register throws UserAlreadyExistsException when email is taken`() {
        every { repo.findByEmail("test@test.com") } returns Mono.just(user)

        StepVerifier.create(service.register("test@test.com", "Test User", "password"))
            .expectError(UserAlreadyExistsException::class.java)
            .verify()
    }

    @Test
    fun `login returns tokens with correct credentials`() {
        every { repo.findByEmail("test@test.com") } returns Mono.just(user)
        every { encoder.matches("password", "hashed") } returns true
        every { tokenService.generateAccessToken(user.id) } returns accessToken
        every { tokenService.generateRefreshToken() } returns refreshToken
        every { tokenService.hashToken("refresh-value") } returns "hashed-refresh"
        every { repo.saveRefreshToken(1L, "hashed-refresh", refreshToken.expiresAt) } returns Mono.empty()

        StepVerifier.create(service.login("test@test.com", "password"))
            .expectNext(authTokens)
            .verifyComplete()
    }

    @Test
    fun `login throws UnauthorizedException with wrong password`() {
        every { repo.findByEmail("test@test.com") } returns Mono.just(user)
        every { encoder.matches("wrong", "hashed") } returns false
        every { encoder.encode(any()) } returns "dummy"

        StepVerifier.create(service.login("test@test.com", "wrong"))
            .expectError(UnauthorizedException::class.java)
            .verify()
    }

    @Test
    fun `login throws UnauthorizedException when user not found`() {
        every { repo.findByEmail("unknown@test.com") } returns Mono.empty()
        every { encoder.encode(any()) } returns "dummy"

        StepVerifier.create(service.login("unknown@test.com", "password"))
            .expectError(UnauthorizedException::class.java)
            .verify()
    }

    @Test
    fun `register saves refresh token to repository`() {
        every { repo.findByEmail("test@test.com") } returns Mono.empty()
        every { encoder.encode("password") } returns "hashed"
        every { repo.create("test@test.com", "Test User", "hashed") } returns Mono.just(user)
        every { tokenService.generateAccessToken(user.id) } returns accessToken
        every { tokenService.generateRefreshToken() } returns refreshToken
        every { tokenService.hashToken("refresh-value") } returns "hashed-refresh"
        every { repo.saveRefreshToken(1L, "hashed-refresh", refreshToken.expiresAt) } returns Mono.empty()

        service.register("test@test.com", "Test User", "password").block()

        verify { repo.saveRefreshToken(1L, "hashed-refresh", refreshToken.expiresAt) }
    }

    @Test
    fun `logout deletes hashed refresh token`() {
        every { tokenService.hashToken("raw-token") } returns "hashed"
        every { repo.deleteRefreshToken("hashed") } returns Mono.empty()

        StepVerifier.create(service.logout("raw-token"))
            .verifyComplete()

        verify { repo.deleteRefreshToken("hashed") }
    }

    @Test
    fun `refreshToken returns new tokens when token is valid`() {
        val stored = StoredRefreshToken(1L, "hashed-old", OffsetDateTime.now(ZoneOffset.UTC).plusDays(30))
        every { tokenService.hashToken("old-token") } returns "hashed-old"
        every { repo.findRefreshToken("hashed-old") } returns Mono.just(stored)
        every { repo.deleteRefreshToken("hashed-old") } returns Mono.empty()
        every { tokenService.generateAccessToken(1L) } returns accessToken
        every { tokenService.generateRefreshToken() } returns refreshToken
        every { tokenService.hashToken("refresh-value") } returns "hashed-new"
        every { repo.saveRefreshToken(1L, "hashed-new", refreshToken.expiresAt) } returns Mono.empty()

        StepVerifier.create(service.refreshToken("old-token"))
            .expectNext(authTokens)
            .verifyComplete()
    }

    @Test
    fun `refreshToken throws UnauthorizedException when token not found`() {
        every { tokenService.hashToken("bad-token") } returns "hashed-bad"
        every { repo.findRefreshToken("hashed-bad") } returns Mono.empty()

        StepVerifier.create(service.refreshToken("bad-token"))
            .expectError(UnauthorizedException::class.java)
            .verify()
    }
}
