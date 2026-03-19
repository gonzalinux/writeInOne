package com.gonzalinux.scheduler

import com.gonzalinux.config.PostSchedulerProperties
import com.gonzalinux.domain.post.PostRepository
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Component
class PublishPostsScheduler(
    postSchedulerProperties: PostSchedulerProperties,
    private val postRepository: PostRepository,
    private val registry: MeterRegistry
) : SchedulerBase(postSchedulerProperties.intervalMs) {

    override fun execute(): Mono<*> =
        Mono.fromCallable { logger.info{"Publishing scheduled if any"} }
            .flatMap { postRepository.publishScheduled() }
            .doOnNext { count ->
                if (count > 0) {
                    logger.info { "Published $count scheduled post(s)" }
                    registry.counter("posts.published", "source", "scheduled").increment(count.toDouble())
                }
            }
}
