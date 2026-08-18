package com.gonzalinux.api

import com.gonzalinux.api.data.ContentBlock
import com.gonzalinux.api.data.CreateDraftArgs
import com.gonzalinux.api.data.CreatePostRequest
import com.gonzalinux.api.data.GetPostArgs
import com.gonzalinux.api.data.JsonRpcError
import com.gonzalinux.api.data.JsonRpcRequest
import com.gonzalinux.api.data.JsonRpcResponse
import com.gonzalinux.api.data.ListPostsArgs
import com.gonzalinux.api.data.ListTagsArgs
import com.gonzalinux.api.data.McpToolDef
import com.gonzalinux.api.data.ToolCallParams
import com.gonzalinux.api.data.ToolCallResult
import com.gonzalinux.common.ApiException
import com.gonzalinux.common.RequestContextHolder.getUserId
import com.gonzalinux.domain.post.PostService
import com.gonzalinux.domain.site.SiteService
import com.gonzalinux.domain.tag.TagService
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

private val logger = KotlinLogging.logger {}

/** Raised for JSON-RPC-level problems (unknown method/tool, bad arguments) — never a domain error. */
private class McpProtocolException(val code: Int, message: String) : RuntimeException(message)

/**
 * Stateless JSON-RPC 2.0 endpoint for MCP clients (Claude Code, Cursor, etc.), hosted in-process
 * instead of distributed as a separate package — see Collaboration.md's "MCP server auth"
 * section. Every request/response is a single `application/json` exchange: no `Mcp-Session-Id`
 * is ever issued and no SSE stream is opened, which the spec allows (session assignment is
 * `MAY`, not `MUST`).
 *
 * v1 exposes only tools that are safe under today's schema — `propose_edit`/`list_versions` need
 * the post-versioning system (Phase2.md #17), which doesn't exist yet, so they're deferred rather
 * than letting an AI-authored edit silently overwrite live published content.
 */
@Component
class McpHandler(
    private val siteService: SiteService,
    private val postService: PostService,
    private val tagService: TagService,
    private val objectMapper: ObjectMapper
) {

    fun handle(request: ServerRequest): Mono<ServerResponse> =
        request.bodyToMono<JsonRpcRequest>()
            .flatMap { rpc ->
                if (rpc.id == null) {
                    // A message with no id is a notification (e.g. notifications/initialized) —
                    // per spec, accepted input gets a 202 with no body.
                    ServerResponse.status(HttpStatus.ACCEPTED).build()
                } else {
                    Mono.deferContextual { ctx -> dispatch(rpc, ctx.getUserId()!!) }
                        .map { result -> JsonRpcResponse(id = rpc.id, result = result) }
                        .onErrorResume { e -> Mono.just(JsonRpcResponse(id = rpc.id, error = toJsonRpcError(e))) }
                        .flatMap { ServerResponse.ok().bodyValue(it) }
                }
            }

    private fun dispatch(rpc: JsonRpcRequest, userId: Long): Mono<Any> =
        when (rpc.method) {
            "initialize" -> Mono.just(initializeResult())
            "tools/list" -> Mono.just(mapOf("tools" to toolDefs))
            "tools/call" -> callTool(rpc.params, userId)
            else -> Mono.error(McpProtocolException(-32601, "Method not found: ${rpc.method}"))
        }

    private fun callTool(params: JsonNode?, userId: Long): Mono<Any> {
        val call = params?.let { objectMapper.treeToValue(it, ToolCallParams::class.java) }
            ?: throw McpProtocolException(-32602, "Missing params")
        val args = call.arguments
        return when (call.name) {
            "list_sites" -> listSites(userId)
            "list_posts" -> listPosts(requireArgs(args, ListPostsArgs::class.java), userId)
            "get_post" -> getPost(requireArgs(args, GetPostArgs::class.java), userId)
            "list_tags" -> listTags(requireArgs(args, ListTagsArgs::class.java), userId)
            "create_draft" -> createDraft(requireArgs(args, CreateDraftArgs::class.java), userId)
            else -> Mono.error(McpProtocolException(-32601, "Unknown tool: ${call.name}"))
        }
    }

    private fun <T> requireArgs(args: JsonNode?, type: Class<T>): T {
        val node = args ?: throw McpProtocolException(-32602, "Missing arguments")
        return try {
            objectMapper.treeToValue(node, type)
        } catch (e: Exception) {
            throw McpProtocolException(-32602, "Invalid arguments: ${e.message}")
        }
    }

    private fun listSites(userId: Long): Mono<Any> =
        siteService.list(userId)
            .map { mapOf("id" to it.id, "name" to it.name, "domain" to it.domain, "role" to it.role?.name) }
            .collectList()
            .map { toolResult(it) }

    private fun listPosts(args: ListPostsArgs, userId: Long): Mono<Any> =
        postService.list(args.siteId, userId, args.page, args.size, args.status, args.tag, args.search)
            .map { toolResult(it) }

    private fun getPost(args: GetPostArgs, userId: Long): Mono<Any> =
        postService.getPublished(args.siteId, args.lang, args.slug, userId)
            .map { (post, translation) -> toolResult(mapOf("post" to post, "translation" to translation)) }

    private fun listTags(args: ListTagsArgs, userId: Long): Mono<Any> =
        tagService.list(args.siteId, userId).collectList().map { toolResult(it) }

    private fun createDraft(args: CreateDraftArgs, userId: Long): Mono<Any> {
        if (args.postId != null) {
            return Mono.error(
                McpProtocolException(
                    -32602,
                    "Editing an existing post isn't supported yet — create_draft only creates new posts."
                )
            )
        }
        val request = CreatePostRequest(coverUrl = args.coverUrl, translations = args.translations, tags = args.tags)
        return postService.create(args.siteId, userId, request).map { toolResult(it) }
    }

    private fun toolResult(value: Any): ToolCallResult =
        ToolCallResult(content = listOf(ContentBlock(text = objectMapper.writeValueAsString(value))))

    private fun initializeResult(): Map<String, Any> = mapOf(
        "protocolVersion" to "2025-06-18",
        "serverInfo" to mapOf("name" to "writeinone", "version" to "1.0"),
        "capabilities" to mapOf("tools" to emptyMap<String, Any>())
    )

    private fun toJsonRpcError(e: Throwable): JsonRpcError = when (e) {
        is McpProtocolException -> JsonRpcError(e.code, e.message ?: "Error")
        is ApiException -> JsonRpcError(codeForStatus(e.status), e.details ?: e.error)
        else -> {
            logger.error(e) { "Unhandled error in MCP tool call" }
            JsonRpcError(-32603, "Internal error")
        }
    }

    private fun codeForStatus(status: HttpStatus): Int = when (status) {
        HttpStatus.NOT_FOUND -> -32001
        HttpStatus.FORBIDDEN -> -32002
        HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY -> -32602
        else -> -32603
    }

    private val toolDefs: List<McpToolDef> = listOf(
        McpToolDef(
            name = "list_sites",
            description = "List all sites the authenticated service account can access, with the caller's role on each.",
            inputSchema = mapOf("type" to "object", "properties" to emptyMap<String, Any>())
        ),
        McpToolDef(
            name = "list_posts",
            description = "List posts for a site, optionally filtered by status, tag, or search text.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "siteId" to mapOf("type" to "integer"),
                    "status" to mapOf(
                        "type" to "string",
                        "enum" to listOf("draft", "scheduled", "published", "archived")
                    ),
                    "tag" to mapOf("type" to "string"),
                    "search" to mapOf("type" to "string"),
                    "page" to mapOf("type" to "integer", "default" to 0),
                    "size" to mapOf("type" to "integer", "default" to 10)
                ),
                "required" to listOf("siteId")
            )
        ),
        McpToolDef(
            name = "get_post",
            description = "Fetch a single post's live (published) content by slug and language.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "siteId" to mapOf("type" to "integer"),
                    "lang" to mapOf("type" to "string", "enum" to listOf("en", "es")),
                    "slug" to mapOf("type" to "string")
                ),
                "required" to listOf("siteId", "lang", "slug")
            )
        ),
        McpToolDef(
            name = "list_tags",
            description = "List tags for a site.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf("siteId" to mapOf("type" to "integer")),
                "required" to listOf("siteId")
            )
        ),
        McpToolDef(
            name = "create_draft",
            description = "Create a new draft post (status=draft) with one or more language translations. " +
                "Does not support editing an existing post.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "siteId" to mapOf("type" to "integer"),
                    "coverUrl" to mapOf("type" to "string"),
                    "translations" to mapOf(
                        "type" to "object",
                        "description" to "Keyed by language code (e.g. \"en\", \"es\")",
                        "additionalProperties" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "title" to mapOf("type" to "string"),
                                "body" to mapOf("type" to "string"),
                                "slug" to mapOf("type" to "string"),
                                "excerpt" to mapOf("type" to "string")
                            ),
                            "required" to listOf("title", "body")
                        )
                    ),
                    "tags" to mapOf("type" to "array", "items" to mapOf("type" to "string"))
                ),
                "required" to listOf("siteId", "translations")
            )
        )
    )
}
