package com.gonzalinux.api.data

import com.fasterxml.jackson.annotation.JsonInclude
import com.gonzalinux.domain.site.NavLink
import tools.jackson.databind.JsonNode

/**
 * JSON-RPC 2.0 envelope for the stateless `/mcp` endpoint. `id` is a raw [JsonNode] because
 * JSON-RPC allows it to be a string, a number, or absent (which marks a notification) — we just
 * echo it back untouched rather than committing to one Kotlin type.
 */
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonNode? = null,
    val method: String,
    val params: JsonNode? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonNode?,
    val result: Any? = null,
    val error: JsonRpcError? = null
)

data class JsonRpcError(val code: Int, val message: String)

data class McpToolDef(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>
)

data class ToolCallParams(
    val name: String,
    val arguments: JsonNode? = null
)

data class ContentBlock(val type: String = "text", val text: String)

data class ToolCallResult(
    val content: List<ContentBlock>,
    val isError: Boolean = false
)

// Per-tool argument shapes, deserialized from ToolCallParams.arguments.

data class ListPostsArgs(
    val siteId: Long,
    val status: String? = null,
    val tag: String? = null,
    val search: String? = null,
    val page: Int = 0,
    val size: Int = 10
)

data class GetPostArgs(val siteId: Long, val lang: String, val slug: String)

data class ListTagsArgs(val siteId: Long)

data class CreateDraftArgs(
    val siteId: Long,
    val translations: Map<String, TranslationInput>,
    val tags: List<String> = emptyList(),
    val coverUrl: String? = null,
    /** Present only to detect and reject the unsupported "edit existing post" case. */
    val postId: Long? = null
)

data class EditArgs(
    val siteId: Long,
    val postId: Long,
    val translations: Map<String, TranslationInput> = emptyMap(),
    val coverUrl: String? = null
)

data class ListVersionsArgs(val siteId: Long, val postId: Long, val lang: String)

/**
 * Either the single-translation form (lang + versionId) or the batch form (versions, keyed by
 * lang) must be supplied — [com.gonzalinux.api.McpHandler.publish] validates that.
 */
data class PublishArgs(
    val siteId: Long,
    val postId: Long,
    val lang: String? = null,
    val versionId: Long? = null,
    val versions: Map<String, Long>? = null
)

data class UnpublishArgs(val siteId: Long, val postId: Long)

data class ScheduleArgs(val siteId: Long, val postId: Long, val scheduledAt: String)

/** All fields optional and mean "leave unchanged" — deliberately omits headHtml/bodyHtml. */
data class LangConfigPatch(
    val footer: String? = null,
    val nav: List<NavLink>? = null,
    val title: String? = null,
    val description: String? = null
)

data class UpdateSiteConfigArgs(
    val siteId: Long,
    val description: String? = null,
    val stylesUrl: String? = null,
    val customCss: String? = null,
    val faviconUrl: String? = null,
    val en: LangConfigPatch? = null,
    val es: LangConfigPatch? = null
)

data class GetDocArgs(val slug: String)
