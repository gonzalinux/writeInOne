package com.gonzalinux.api

import com.gonzalinux.api.data.ContentBlock
import com.gonzalinux.api.data.CreateDraftArgs
import com.gonzalinux.api.data.CreatePostRequest
import com.gonzalinux.api.data.EditArgs
import com.gonzalinux.api.data.GetPostArgs
import com.gonzalinux.api.data.JsonRpcError
import com.gonzalinux.api.data.JsonRpcRequest
import com.gonzalinux.api.data.JsonRpcResponse
import com.gonzalinux.api.data.ListPostsArgs
import com.gonzalinux.api.data.ListTagsArgs
import com.gonzalinux.api.data.ListVersionsArgs
import com.gonzalinux.api.data.McpToolDef
import com.gonzalinux.api.data.PublishArgs
import com.gonzalinux.api.data.ScheduleArgs
import com.gonzalinux.api.data.ToolCallParams
import com.gonzalinux.api.data.ToolCallResult
import com.gonzalinux.api.data.UpdatePostRequest
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
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

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
 * `edit`, `list_versions`, `publish` and `schedule` build on the post-versioning system
 * (Phase2.md #17): editing a translation that has already gone live creates a new **draft** version
 * rather than touching what's published, and `publish`/`schedule` are the only ways to move draft
 * content live. `publish` wraps [PostService.publishVersion] — it publishes one named version and,
 * on that translation's first-ever publish, also brings the post itself live (see that method's
 * doc). There's no tool-level restriction on who may publish — `PostService` already enforces the
 * site role (`writer` accounts get a normal 403/`-32002` from `requirePublish()`), so access is
 * controlled by which role a service account is granted, same as every other write path in this
 * app.
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
            "edit" -> edit(requireArgs(args, EditArgs::class.java), userId)
            "list_versions" -> listVersions(requireArgs(args, ListVersionsArgs::class.java), userId)
            "publish" -> publish(requireArgs(args, PublishArgs::class.java), userId)
            "schedule" -> schedule(requireArgs(args, ScheduleArgs::class.java), userId)
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

    private fun edit(args: EditArgs, userId: Long): Mono<Any> {
        val request = UpdatePostRequest(coverUrl = null, translations = args.translations, tags = null)
        return postService.update(args.postId, args.siteId, userId, request).map { toolResult(it) }
    }

    private fun listVersions(args: ListVersionsArgs, userId: Long): Mono<Any> =
        postService.listVersions(args.postId, args.siteId, userId, args.lang).collectList().map { toolResult(it) }

    private fun publish(args: PublishArgs, userId: Long): Mono<Any> =
        postService.publishVersion(args.postId, args.siteId, userId, args.lang, args.versionId).map { toolResult(it) }

    private fun schedule(args: ScheduleArgs, userId: Long): Mono<Any> {
        val scheduledAt = try {
            OffsetDateTime.parse(args.scheduledAt)
        } catch (e: DateTimeParseException) {
            throw McpProtocolException(-32602, "Invalid scheduledAt — expected ISO-8601 with an offset: ${e.message}")
        }
        return postService.schedule(args.postId, args.siteId, userId, scheduledAt).map { toolResult(it) }
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
        ),
        McpToolDef(
            name = "edit",
            description = "Edit an existing post's translation(s). For a language that already has a live " +
                "(published) version, this creates a new draft version on top of it and never touches what's " +
                "published — call publish to push it live. For a language the post doesn't have yet, the " +
                "translation is created directly, same as create_draft, since there's nothing published yet " +
                "to protect. Does not touch the post's cover image or tags.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "siteId" to mapOf("type" to "integer"),
                    "postId" to mapOf("type" to "integer"),
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
                    )
                ),
                "required" to listOf("siteId", "postId", "translations")
            )
        ),
        McpToolDef(
            name = "list_versions",
            description = "List the version history (draft and published, newest first) for one " +
                "translation of a post.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "siteId" to mapOf("type" to "integer"),
                    "postId" to mapOf("type" to "integer"),
                    "lang" to mapOf("type" to "string", "enum" to listOf("en", "es"))
                ),
                "required" to listOf("siteId", "postId", "lang")
            )
        ),
        McpToolDef(
            name = "publish",
            description = "Publish a specific draft version of a translation (from create_draft or edit), " +
                "making it the live content. On that translation's first-ever publish, this also brings the " +
                "whole post live — no separate post-level publish step needed. Also how rollback works " +
                "(publish an older version again). Doesn't affect other translations on the same post that " +
                "are still drafts. Requires editor or admin on the site; a writer-role service account gets a " +
                "normal permission error.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "siteId" to mapOf("type" to "integer"),
                    "postId" to mapOf("type" to "integer"),
                    "lang" to mapOf("type" to "string", "enum" to listOf("en", "es")),
                    "versionId" to mapOf(
                        "type" to "integer",
                        "description" to "From create_draft/edit's latestVersions, or from list_versions"
                    )
                ),
                "required" to listOf("siteId", "postId", "lang", "versionId")
            )
        ),
        McpToolDef(
            name = "schedule",
            description = "Schedule a post to be published automatically at a future time. Same role " +
                "requirement as publish.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "siteId" to mapOf("type" to "integer"),
                    "postId" to mapOf("type" to "integer"),
                    "scheduledAt" to mapOf(
                        "type" to "string",
                        "description" to "ISO-8601 timestamp with a UTC offset, e.g. \"2026-03-01T09:00:00Z\""
                    )
                ),
                "required" to listOf("siteId", "postId", "scheduledAt")
            )
        )
    )
}
