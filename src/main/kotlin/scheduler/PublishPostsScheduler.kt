package com.gonzalinux.scheduler

import com.gonzalinux.config.PostSchedulerProperties
import com.gonzalinux.config.SchedulersProperties
import com.gonzalinux.domain.post.PostRepository
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Component
class PublishPostsScheduler(
    postSchedulerProperties: PostSchedulerProperties,
    schedulersProperties: SchedulersProperties,
    private val postRepository: PostRepository,
    private val registry: MeterRegistry,
    private val tx: TransactionalOperator
) : SchedulerBase(postSchedulerProperties.intervalMs, schedulersProperties.enabled) {

    // One transaction: if a translation can't go live (e.g. slug clash) the post stays scheduled and is retried.
    override fun execute(): Mono<*> =
        postRepository.publishScheduled()
            .concatMap { postRepository.publishInitialVersions(it).thenReturn(it) }
            .`as`(tx::transactional)
            .count()
            .doOnNext { count ->
                if (count > 0) {
                    logger.info { "Published $count scheduled post(s)" }
                    registry.counter("posts.published", "source", "scheduled").increment(count.toDouble())
                }
            }
}
