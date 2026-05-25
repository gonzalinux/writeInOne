package com.gonzalinux.domain.user

import com.gonzalinux.common.BadRequestException
import com.gonzalinux.common.UnauthorizedException
import com.gonzalinux.common.UserAlreadyExistsException
import com.gonzalinux.config.PasswordEncoder
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty

private val logger = KotlinLogging.logger {}

private const val VERIFICATION_EXPIRY_MIN = 5L
private const val RESET_EXPIRY_MIN = 5L

@Service
class UserService(
    private val repo: UserRepository,
    private val encoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val emailClient: EmailClient,
    private val registry: MeterRegistry
) {

    fun register(email: String, displayName: String, password: String): Mono<AuthTokens> {
        return repo.findByEmail(email)
            .flatMap<AuthTokens> { existing ->
                if (existing.emailVerified) {
                    Mono.error(UserAlreadyExistsException(email))
                } else {
                    repo.deleteById(existing.id).then(Mono.empty())
                }
            }
            .switchIfEmpty(
                repo.create(
                    email = email,
                    displayName = displayName,
                    passwordHash = encoder.encode(password)
                ).flatMap { user ->
                    issueTokens(user.id)
                        .flatMap { tokens ->
                            sendVerificationEmail(user.id, user.email, user.displayName)
                                .onErrorComplete()
                                .thenReturn(tokens)
                        }
                }.doOnSuccess { registry.counter("auth.registrations").increment() }
            )
    }

    fun login(email: String, password: String): Mono<AuthTokens> {
        return repo.findVerifiedByEmail(email)
            .switchIfEmpty {
                encoder.encode("Just to prevent timing attacks")
                Mono.empty()
            }
            .filter { encoder.matches(password, it.passwordHash) }
            .flatMap { user ->
                logger.info { "User logged in [userId=${user.id}]" }
                issueTokens(user.id)
            }
            .doOnSuccess { registry.counter("auth.logins", "result", "success").increment() }
            .switchIfEmpty {
                logger.warn { "Failed login attempt [email=$email]" }
                registry.counter("auth.logins", "result", "failure").increment()
                Mono.error(UnauthorizedException())
            }
    }

    fun logout(refreshToken: String): Mono<Void> {
        val tokenHash = tokenService.hashToken(refreshToken)
        return repo.deleteRefreshToken(tokenHash)
            .doOnSuccess { logger.info { "User logged out" } }
    }

    fun refreshToken(refreshToken: String): Mono<AuthTokens> {
        val hashedToken = tokenService.hashToken(refreshToken)
        return repo.findRefreshToken(hashedToken)
            .switchIfEmpty {
                Mono.error(UnauthorizedException())
            }
            .flatMap { stored ->
                issueTokens(stored.userId)
                    .flatMap { token ->
                        logger.debug { "Token refreshed [userId=${stored.userId}]" }
                        repo.deleteRefreshToken(stored.tokenHash)
                            .thenReturn(token)
                    }
            }
            .doOnSuccess { registry.counter("auth.token.refreshes").increment() }
    }

    fun sendVerificationEmail(userId: Long, email: String, displayName: String): Mono<Void> {
        val otp = tokenService.generateOtp(VERIFICATION_EXPIRY_MIN)
        val tokenHash = tokenService.hashToken(otp.value)
        return repo.deleteEmailVerificationTokensByUserId(userId)
            .then(repo.saveEmailVerificationToken(userId, tokenHash, otp.expiresAt))
            .then(emailClient.sendVerificationEmail(email, displayName, otp.value))
            .doOnSuccess { logger.info { "Verification email sent [userId=$userId]" } }
    }

    fun verifyEmail(email: String, code: String): Mono<Void> {
        val tokenHash = tokenService.hashToken(code)
        return repo.findByEmail(email)
            .switchIfEmpty { Mono.error(BadRequestException("Invalid or expired code")) }
            .flatMap { user ->
                repo.findEmailVerificationToken(user.id, tokenHash)
                    .switchIfEmpty { Mono.error(BadRequestException("Invalid or expired code")) }
                    .flatMap { stored ->
                        repo.updateEmailVerified(stored.userId)
                            .then(repo.deleteEmailVerificationToken(tokenHash))
                            .doOnSuccess { logger.info { "Email verified [userId=${stored.userId}]" } }
                    }
            }
    }

    fun requestPasswordReset(email: String): Mono<Void> {
        return repo.findVerifiedByEmail(email)
            .flatMap { user ->
                val otp = tokenService.generateOtp(RESET_EXPIRY_MIN)
                val tokenHash = tokenService.hashToken(otp.value)
                repo.deletePasswordResetTokensByUserId(user.id)
                    .then(repo.savePasswordResetToken(user.id, tokenHash, otp.expiresAt))
                    .then(emailClient.sendPasswordResetEmail(user.email, otp.value))
                    .doOnSuccess { logger.info { "Password reset email sent [userId=${user.id}]" } }
            }
            // always succeed — don't leak whether email exists
            .then()
    }

    fun resetPassword(email: String, code: String, newPassword: String): Mono<Void> {
        val tokenHash = tokenService.hashToken(code)
        return repo.findVerifiedByEmail(email)
            .switchIfEmpty { Mono.error(BadRequestException("Invalid or expired code")) }
            .flatMap { user ->
                repo.findPasswordResetToken(user.id, tokenHash)
                    .switchIfEmpty { Mono.error(BadRequestException("Invalid or expired code")) }
                    .flatMap { stored ->
                        repo.updatePassword(stored.userId, encoder.encode(newPassword))
                            .then(repo.deletePasswordResetToken(tokenHash))
                            .doOnSuccess { logger.info { "Password reset [userId=${stored.userId}]" } }
                    }
            }
    }

    fun resendVerificationEmail(userId: Long): Mono<Void> {
        return repo.findByUserId(userId)
            .flatMap { user ->
                if (user.emailVerified) {
                    Mono.error(BadRequestException("Email is already verified"))
                } else {
                    sendVerificationEmail(user.id, user.email, user.displayName)
                }
            }
    }

    private fun issueTokens(userId: Long): Mono<AuthTokens> {
        val accessToken = tokenService.generateAccessToken(userId)
        val refreshToken = tokenService.generateRefreshToken()
        val tokenHash = tokenService.hashToken(refreshToken.value)

        return repo.saveRefreshToken(userId, tokenHash, refreshToken.expiresAt)
            .thenReturn(AuthTokens(accessToken, refreshToken))
    }
}
