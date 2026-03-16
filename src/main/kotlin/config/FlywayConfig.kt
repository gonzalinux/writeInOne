package com.gonzalinux.config

import org.flywaydb.core.Flyway
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "spring.flyway")
data class FlywayProperties(
    val url: String,
    val user: String,
    val password: String,
)

@Configuration
@EnableConfigurationProperties(FlywayProperties::class)
class FlywayConfig(private val props: FlywayProperties) {

    @Bean(initMethod = "migrate")
    fun flyway(): Flyway = Flyway.configure()
        .dataSource(props.url, props.user, props.password)
        .locations("classpath:db/migration")
        .load()
}
