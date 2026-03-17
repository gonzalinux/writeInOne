package com.gonzalinux.scheduler

import com.gonzalinux.config.PostSchedulerProperties
import com.gonzalinux.domain.post.PostRepository
import kotlinx.coroutines.reactor.awaitSingleOrNull
import mu.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class PublishPostsScheduler(
    private val postSchedulerProperties: PostSchedulerProperties,
    private val postRepository: PostRepository
) : SchedulerBase(postSchedulerProperties.intervalMs) {

    override suspend fun start() {
        val count = postRepository.publishScheduled().awaitSingleOrNull() ?: 0
        if (count > 0) logger.info { "Published $count scheduled post(s)" }
    }
}
