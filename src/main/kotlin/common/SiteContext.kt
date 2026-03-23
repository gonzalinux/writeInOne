package com.gonzalinux.common

import com.gonzalinux.domain.site.Site
import reactor.util.context.Context
import reactor.util.context.ContextView

object SiteContextHolder {
    const val CONTEXT_KEY = "SITE_CONTEXT"
    const val PREFIX_CONTEXT_KEY = "BLOG_PREFIX"

    fun Context.withSite(site: Site): Context = put(CONTEXT_KEY, site)
    fun Context.withPrefix(prefix: String): Context = put(PREFIX_CONTEXT_KEY, prefix)

    fun ContextView.getSite(): Site? = getOrDefault(CONTEXT_KEY, null)
    fun ContextView.getPrefix(): String = getOrDefault(PREFIX_CONTEXT_KEY, "")!!
}
