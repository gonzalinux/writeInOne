package com.gonzalinux.config

import io.micrometer.core.instrument.config.MeterFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MetricsConfig {

    /**
     * Backstop against a `path` tag explosion. [MetricsFilter] already collapses unrouted requests
     * into `unknown`, so this should never trigger; if some path ever leaks through anyway, new
     * series are dropped instead of taking the Prometheus cluster down with them.
     */
    @Bean
    fun pathTagCardinalityLimit(): MeterFilter =
        MeterFilter.maximumAllowableTags("http", "path", MAX_PATH_TAG_VALUES, MeterFilter.deny())

    companion object {
        /** Well above the ~30 routes plus static assets we actually serve. */
        const val MAX_PATH_TAG_VALUES = 200
    }
}
