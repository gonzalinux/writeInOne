package com.gonzalinux.scheduler

import com.gonzalinux.config.PostSchedulerProperties
import com.gonzalinux.config.SchedulersProperties
import com.gonzalinux.domain.post.PostRepository
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Component
class PublishPostsScheduler(
    postSchedulerProperties: PostSchedulerProperties,
    schedulersProperties: SchedulersProperties,
    private val postRepository: PostRepository,
    private val registry: MeterRegistry
) : SchedulerBase(postSchedulerProperties.intervalMs, schedulersProperties.enabled) {

    override fun execute(): Mono<*> =
           postRepository.publishScheduled()
            .doOnNext { count ->
                if (count > 0) {
                    logger.info { "Published $count scheduled post(s)" }
                    registry.counter("posts.published", "source", "scheduled").increment(count.toDouble())
                }
            }
}
