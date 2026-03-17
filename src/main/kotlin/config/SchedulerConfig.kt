package com.gonzalinux.config

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "post-scheduler")
data class PostSchedulerProperties (
    val intervalMs: Long,
)

@ConfigurationProperties(prefix = "token-scheduler")
data class TokenCleanerProperties (
    val intervalMin: Long,
    val limitDeleted: Int,
)

