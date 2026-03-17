package com.gonzalinux.common

import com.gonzalinux.domain.site.Site
import reactor.util.context.Context
import reactor.util.context.ContextView

object SiteContextHolder {
    const val CONTEXT_KEY = "SITE_CONTEXT"

    fun Context.withSite(site: Site): Context = put(CONTEXT_KEY, site)

    fun ContextView.getSite(): Site? = getOrDefault(CONTEXT_KEY, null)
}
