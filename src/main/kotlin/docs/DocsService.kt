package com.gonzalinux.docs

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.stereotype.Service

data class DocPage(
    val slug: String,
    val group: String,
    val groupLabel: String,
    val order: Int,
    val title: String,
    val renderedHtml: String,
    val rawMarkdown: String
)

data class DocGroup(val name: String, val label: String, val order: Int, val pages: List<DocPage>)

@Service
class DocsService(
    private val mdParser: Parser,
    private val mdRenderer: HtmlRenderer,
    private val resourceResolver: ResourcePatternResolver
) {

    final val groups: List<DocGroup>
    private val pagesBySlug: Map<String, DocPage>

    init {
        val resources = resourceResolver.getResources("classpath:docs/**/*.md")

        val pages = resources
            .map { resource ->
                val relativePath = resource.url.toString()
                    .substringAfterLast("/docs/")
                    .removeSuffix(".md")
                val raw = resource.inputStream.bufferedReader().use { it.readText() }
                val (groupOrder, group) = splitOrderPrefix(relativePath.substringBefore('/'))
                val (order, fileName) = splitOrderPrefix(relativePath.substringAfterLast('/'))
                val title = raw.lineSequence().firstOrNull { it.startsWith("# ") }
                    ?.removePrefix("# ")?.trim()
                    ?: humanize(fileName)

                DocPage(
                    slug = "$group/$fileName",
                    group = group,
                    groupLabel = humanize(group),
                    order = order,
                    title = title,
                    renderedHtml = mdRenderer.render(mdParser.parse(raw)),
                    rawMarkdown = raw
                ) to groupOrder
            }
            .sortedWith(compareBy({ it.first.order }, { it.first.title }))

        pagesBySlug = pages.associate { (page, _) -> page.slug to page }
        groups = pages
            .groupBy { (page, groupOrder) -> Triple(page.group, page.groupLabel, groupOrder) }
            .map { (key, groupPages) -> DocGroup(key.first, key.second, key.third, groupPages.map { it.first }) }
            .sortedWith(compareBy({ it.order }, { it.label }))
    }

    fun find(slug: String): DocPage? = pagesBySlug[slug.trim('/')]

    fun firstSlug(): String? = groups.firstOrNull()?.pages?.firstOrNull()?.slug

    /**
     * Strips an optional leading "N-" ordering prefix (e.g. "1-quickstart" -> 1 to "quickstart").
     * Segments without a numeric prefix sort after ordered ones, alphabetically.
     */
    private fun splitOrderPrefix(segment: String): Pair<Int, String> {
        val match = Regex("^(\\d+)-(.+)$").find(segment)
        return if (match != null) match.groupValues[1].toInt() to match.groupValues[2]
        else Int.MAX_VALUE to segment
    }

    private fun humanize(name: String): String =
        if (name.equals("api", ignoreCase = true)) "API"
        else name.split('-', '_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
