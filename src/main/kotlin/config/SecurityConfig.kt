package com.gonzalinux.config

import at.favre.lib.crypto.bcrypt.BCrypt
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoder()
}

class PasswordEncoder {
    fun encode(password: String): String =
        BCrypt.withDefaults().hashToString(12, password.toCharArray())

    fun matches(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified
}
