package com.gonzalinux.domain.user

import com.gonzalinux.common.UnauthorizedException
import com.gonzalinux.common.UserAlreadyExistsException
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

    private val repo = mockk<UserRepository>()
    private val encoder = mockk<PasswordEncoder>()
    private val tokenService = mockk<TokenService>()
    private val service = UserService(repo, encoder, tokenService)

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
}
