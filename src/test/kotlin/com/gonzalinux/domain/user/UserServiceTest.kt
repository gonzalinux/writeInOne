package com.gonzalinux.domain.user

import com.gonzalinux.common.BadRequestException
import com.gonzalinux.common.UnauthorizedException
import com.gonzalinux.common.UserAlreadyExistsException
import com.gonzalinux.config.PasswordEncoder
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Suppress("ReactiveStreamsUnusedPublisher")
class UserServiceTest {

    private val repo = mockk<UserRepository>(relaxed = true)
    private val encoder = mockk<PasswordEncoder>(relaxed = true)
    private val tokenService = mockk<TokenService>(relaxed = true)
    private val emailClient = mockk<EmailClient>(relaxed = true)
    private val registry = mockk<MeterRegistry>(relaxed = true)
    private val service = UserService(repo, encoder, tokenService, emailClient, registry)

    private val now = OffsetDateTime.now(ZoneOffset.UTC)

    private val verifiedUser = User(
        id = 1L,
        email = "test@test.com",
        passwordHash = "hashed",
        displayName = "Test User",
        emailVerified = true,
        createdAt = now,
        updatedAt = now
    )

    private val unverifiedUser = verifiedUser.copy(id = 2L, emailVerified = false)

    private val otp = OpaqueToken("123456", now.plusMinutes(5))
    private val accessToken = AccessToken("access-jwt")
    private val refreshToken = RefreshToken("refresh-value", now.plusDays(30))
    private val authTokens = AuthTokens(accessToken, refreshToken)

    private fun mockIssueTokens(userId: Long) {
        every { tokenService.generateAccessToken(userId) } returns accessToken
        every { tokenService.generateRefreshToken() } returns refreshToken
        every { tokenService.hashToken("refresh-value") } returns "hashed-refresh"
        every { repo.saveRefreshToken(userId, "hashed-refresh", refreshToken.expiresAt) } returns Mono.empty()
    }

    // Relaxed mocks answer a Mono-returning call with a mock Mono that never signals, so every hop
    // of an OTP-email chain has to be stubbed or the chain simply never completes.
    private fun mockVerificationEmail(userId: Long) {
        every { tokenService.generateOtp(any()) } returns otp
        every { tokenService.hashToken("123456") } returns "hashed-otp"
        every { repo.deleteEmailVerificationTokensByUserId(userId) } returns Mono.empty()
        every { repo.saveEmailVerificationToken(userId, "hashed-otp", otp.expiresAt) } returns Mono.empty()
        every { emailClient.sendVerificationEmail(any(), any(), any()) } returns Mono.empty()
    }

    private fun mockPasswordResetEmail(userId: Long) {
        every { tokenService.generateOtp(any()) } returns otp
        every { tokenService.hashToken("123456") } returns "hashed-otp"
        every { repo.deletePasswordResetTokensByUserId(userId) } returns Mono.empty()
        every { repo.savePasswordResetToken(userId, "hashed-otp", otp.expiresAt) } returns Mono.empty()
        every { emailClient.sendPasswordResetEmail(any(), any()) } returns Mono.empty()
    }

    // --- register ---

    @Test
    fun `register creates user and sends verification email`() {
        every { repo.findByEmail("test@test.com") } returns Mono.empty()
        every { encoder.encode("password") } returns "hashed"
        every { repo.create("test@test.com", "Test User", "hashed") } returns Mono.just(verifiedUser)
        mockVerificationEmail(verifiedUser.id)

        StepVerifier.create(service.register("test@test.com", "Test User", "password"))
            .verifyComplete()

        verify { emailClient.sendVerificationEmail("test@test.com", "Test User", "123456") }
    }

    @Test
    fun `register throws UserAlreadyExistsException when verified email is taken`() {
        every { repo.findByEmail("test@test.com") } returns Mono.just(verifiedUser)

        StepVerifier.create(service.register("test@test.com", "Test User", "password"))
            .expectError(UserAlreadyExistsException::class.java)
            .verify()
    }

    @Test
    fun `register deletes unverified user and creates fresh account`() {
        every { repo.findByEmail("test@test.com") } returns Mono.just(unverifiedUser)
        every { repo.deleteById(unverifiedUser.id) } returns Mono.empty()
        every { encoder.encode("password") } returns "hashed"
        every { repo.create("test@test.com", "Test User", "hashed") } returns Mono.just(verifiedUser)
        mockVerificationEmail(verifiedUser.id)

        StepVerifier.create(service.register("test@test.com", "Test User", "password"))
            .verifyComplete()

        verify { repo.deleteById(unverifiedUser.id) }
    }

    // --- login ---

    @Test
    fun `login returns tokens with correct credentials`() {
        every { repo.findVerifiedByEmail("test@test.com") } returns Mono.just(verifiedUser)
        every { encoder.matches("password", "hashed") } returns true
        mockIssueTokens(verifiedUser.id)

        StepVerifier.create(service.login("test@test.com", "password"))
            .expectNext(authTokens)
            .verifyComplete()
    }

    @Test
    fun `login throws UnauthorizedException with wrong password`() {
        every { repo.findVerifiedByEmail("test@test.com") } returns Mono.just(verifiedUser)
        every { encoder.matches("wrong", "hashed") } returns false

        StepVerifier.create(service.login("test@test.com", "wrong"))
            .expectError(UnauthorizedException::class.java)
            .verify()
    }

    @Test
    fun `login throws UnauthorizedException when user not found`() {
        every { repo.findVerifiedByEmail("unknown@test.com") } returns Mono.empty()

        StepVerifier.create(service.login("unknown@test.com", "password"))
            .expectError(UnauthorizedException::class.java)
            .verify()
    }

    // --- verifyEmail ---

    @Test
    fun `verifyEmail marks user as verified, removes token, and returns JWT tokens`() {
        val stored = StoredEmailToken(verifiedUser.id, "hashed-otp", now.plusMinutes(5))
        every { tokenService.hashToken("123456") } returns "hashed-otp"
        every { repo.findByEmail("test@test.com") } returns Mono.just(verifiedUser)
        every { repo.findEmailVerificationToken(verifiedUser.id, "hashed-otp") } returns Mono.just(stored)
        every { repo.updateEmailVerified(verifiedUser.id) } returns Mono.empty()
        every { repo.deleteEmailVerificationToken("hashed-otp") } returns Mono.empty()
        mockIssueTokens(verifiedUser.id)

        StepVerifier.create(service.verifyEmail("test@test.com", "123456"))
            .expectNext(authTokens)
            .verifyComplete()

        verify { repo.updateEmailVerified(verifiedUser.id) }
        verify { repo.deleteEmailVerificationToken("hashed-otp") }
    }

    @Test
    fun `verifyEmail throws BadRequestException for wrong code`() {
        every { tokenService.hashToken("wrong") } returns "hashed-wrong"
        every { repo.findByEmail("test@test.com") } returns Mono.just(verifiedUser)
        every { repo.findEmailVerificationToken(verifiedUser.id, "hashed-wrong") } returns Mono.empty()

        StepVerifier.create(service.verifyEmail("test@test.com", "wrong"))
            .expectError(BadRequestException::class.java)
            .verify()
    }

    // --- requestPasswordReset ---

    @Test
    fun `requestPasswordReset sends email for verified user`() {
        every { repo.findVerifiedByEmail("test@test.com") } returns Mono.just(verifiedUser)
        mockPasswordResetEmail(verifiedUser.id)

        StepVerifier.create(service.requestPasswordReset("test@test.com"))
            .verifyComplete()

        verify { emailClient.sendPasswordResetEmail("test@test.com", "123456") }
    }

    @Test
    fun `requestPasswordReset completes silently for unknown email`() {
        every { repo.findVerifiedByEmail("ghost@test.com") } returns Mono.empty()

        StepVerifier.create(service.requestPasswordReset("ghost@test.com"))
            .verifyComplete()

        verify(exactly = 0) { emailClient.sendPasswordResetEmail(any(), any()) }
    }

    // --- resetPassword ---

    @Test
    fun `resetPassword updates password with valid code`() {
        val stored = StoredEmailToken(verifiedUser.id, "hashed-otp", now.plusMinutes(5))
        every { tokenService.hashToken("123456") } returns "hashed-otp"
        every { repo.findVerifiedByEmail("test@test.com") } returns Mono.just(verifiedUser)
        every { repo.findPasswordResetToken(verifiedUser.id, "hashed-otp") } returns Mono.just(stored)
        every { encoder.encode("newpassword") } returns "new-hashed"
        every { repo.updatePassword(verifiedUser.id, "new-hashed") } returns Mono.empty()
        every { repo.deletePasswordResetToken("hashed-otp") } returns Mono.empty()

        StepVerifier.create(service.resetPassword("test@test.com", "123456", "newpassword"))
            .verifyComplete()

        verify { repo.updatePassword(verifiedUser.id, "new-hashed") }
        verify { repo.deletePasswordResetToken("hashed-otp") }
    }

    @Test
    fun `resetPassword throws BadRequestException for invalid code`() {
        every { tokenService.hashToken("wrong") } returns "hashed-wrong"
        every { repo.findVerifiedByEmail("test@test.com") } returns Mono.just(verifiedUser)
        every { repo.findPasswordResetToken(verifiedUser.id, "hashed-wrong") } returns Mono.empty()

        StepVerifier.create(service.resetPassword("test@test.com", "wrong", "newpassword"))
            .expectError(BadRequestException::class.java)
            .verify()
    }

    // --- logout / refresh (unchanged logic, keep coverage) ---

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
        val stored = StoredRefreshToken(1L, "hashed-old", now.plusDays(30))
        every { tokenService.hashToken("old-token") } returns "hashed-old"
        every { repo.findRefreshToken("hashed-old") } returns Mono.just(stored)
        every { repo.deleteRefreshToken("hashed-old") } returns Mono.empty()
        mockIssueTokens(1L)
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
