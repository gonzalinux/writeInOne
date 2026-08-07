package com.gonzalinux.docs

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.stereotype.Service

data class DocPage(
    val slug: String,
    val group: String,
    val groupLabel: String,
    val title: String,
    val renderedHtml: String,
    val rawMarkdown: String
)

data class DocGroup(val name: String, val label: String, val pages: List<DocPage>)

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
                val group = relativePath.substringBefore('/')
                val fileName = relativePath.substringAfterLast('/')
                val title = raw.lineSequence().firstOrNull { it.startsWith("# ") }
                    ?.removePrefix("# ")?.trim()
                    ?: humanize(fileName)

                DocPage(
                    slug = relativePath,
                    group = group,
                    groupLabel = humanize(group),
                    title = title,
                    renderedHtml = mdRenderer.render(mdParser.parse(raw)),
                    rawMarkdown = raw
                )
            }
            .sortedBy { it.title }

        pagesBySlug = pages.associateBy { it.slug }
        groups = pages.groupBy { it.group }
            .toSortedMap()
            .map { (name, groupPages) -> DocGroup(name, humanize(name), groupPages) }
    }

    fun find(slug: String): DocPage? = pagesBySlug[slug.trim('/')]

    fun firstSlug(): String? = groups.firstOrNull()?.pages?.firstOrNull()?.slug

    private fun humanize(name: String): String =
        if (name.equals("api", ignoreCase = true)) "API"
        else name.split('-', '_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
