package com.gonzalinux.blogs

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

private val RSS_DATE_FMT = DateTimeFormatter
    .ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)

fun buildRss(
    siteTitle: String,
    siteDescription: String,
    domain: String,
    lang: String,
    posts: List<BlogPostSummary>,
    prefix: String
): String {
    val prefix= prefix.removePrefix("/").removeSuffix("/")
    val baseUrl = "https://$domain/$prefix"
    val feedUrl = "$baseUrl/$lang/rss.xml"
    val htmlUrl = "$baseUrl/$lang"

    val items = posts.joinToString("\n") { item ->
        val url     = "$baseUrl/$lang/${item.translation.slug}"
        val pubDate = item.post.publishedAt
            ?.atZoneSameInstant(ZoneOffset.UTC)
            ?.format(RSS_DATE_FMT) ?: ""
        val desc = item.translation.excerpt ?: ""
        """        <item>
            <title>${item.translation.title.escXml()}</title>
            <link>$url</link>
            <guid isPermaLink="true">$url</guid>
            <pubDate>$pubDate</pubDate>${if (desc.isNotBlank()) "\n            <description>${desc.escXml()}</description>" else ""}${if (item.post.coverUrl != null) "\n            <enclosure url=\"${item.post.coverUrl}\" type=\"image/jpeg\" length=\"0\"/>" else ""}
        </item>"""
    }

    return """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
    <channel>
        <title>${siteTitle.escXml()}</title>
        <link>$htmlUrl</link>
        <description>${siteDescription.escXml()}</description>
        <language>$lang</language>
        <atom:link href="$feedUrl" rel="self" type="application/rss+xml"/>
$items
    </channel>
</rss>"""
}

private fun String.escXml() = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
