When I started building WriteInOne, a personal blogging backend in Kotlin with Spring Boot WebFlux,
I obviously went for Spring Security to handle JWT with a refresh token in cookies. I had experience with it from
previous non-WebFlux projects and thought it was the best option.

But as I started wiring things up, something felt off.

## Spring Security's Mismatch with Functional Routing

Spring Security was designed for the MVC world — `@RestController`, `@RequestMapping`, annotations everywhere. In that
model, routes are scattered across dozens of controller classes, so centralizing security rules in one place makes
sense. You declare your rules in a `SecurityWebFilterChain` bean:

```kotlin
.authorizeExchange { auth ->
    auth.pathMatchers("/auth/**").permitAll()
    auth.pathMatchers("/sites/**").authenticated()
    auth.anyExchange().authenticated()
}
```

The problem? I'm using WebFlux functional routing. All my routes already live in one place — the router. So now I have
two places declaring the same information: the router says `POST /sites` exists, and the security config says
`/sites/**` requires auth. Change one, forget the other, and you have a bug.

It's not that Spring Security is wrong — it's that it was adapted from MVC rather than redesigned for functional
routing. The seams show.

## What I Did Instead

The mental model I kept coming back to was simple: **two pipelines**. This idea isn't new — it's how routers work
in [Elysia.js](https://elysiajs.com/) and [Phoenix](https://www.phoenixframework.org/) (Elixir). Both frameworks let you
group routes and apply plugs or middleware at the group level, keeping access rules next to the routes they protect. I
wanted the same clarity in my Kotlin backend.

Public routes — auth endpoints, public blog pages — just handle the request. Protected routes go through a filter first
that validates the JWT and either lets the request through or returns a 401.

In the router this looks like:

```kotlin
fun routes() = publicRoutes().and(protectedRoutes())

fun publicRoutes() = route()
    .POST("/auth/register", authHandler::register)
    .POST("/auth/login", authHandler::login)
    .build()

fun protectedRoutes() = route()
    .POST("/sites", siteHandler::create)
    .GET("/sites", siteHandler::list)
    .build()
    .filter(jwtAuthFilter)
```

The filter itself is a plain class implementing `HandlerFilterFunction`:

```kotlin
@Component
class JwtAuthFilter(private val tokenService: TokenService) : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> {
        val token = request.cookies()[ACCESS_TOKEN_COOKIE]?.firstOrNull()?.value
            ?: throw UnauthorizedException()

        return Mono.fromCallable { tokenService.getUserIdFromToken(token) }
            .map { userId -> RequestContext(userId, extractRequestId(request)) }
            .onErrorMap { throw UnauthorizedException() }
            .flatMap { reqContext ->
                next.handle(request).contextWrite { it.withRequestContext(reqContext) }
            }
    }
}
```

There's something worth highlighting here: the `userId` extracted from the token gets wrapped into a `RequestContext`
and written into Reactor's `ContextView` via `contextWrite`. Any handler downstream can then pull it out with
`Mono.deferContextual { ctx -> ctx.getRequestContext() }` — no thread-locals, no static holders, just the reactive
context flowing through the pipeline.

That same `RequestContext` also gets bridged into MDC. A `ThreadLocalAccessor` registered via Micrometer's
`ContextRegistry` picks it up and calls `MDC.put("userId", ...)` automatically whenever the context propagates to a
thread. The result is that `userId` and `requestId` show up in every log line for that request with zero extra
plumbing in the handlers — just configure your log pattern to include `%X{userId}` and it's there.

`HandlerFilterFunction` is a WebFlux interface designed exactly for this — filtering handler functions in a router. No
Spring Security involved, no framework magic, just a class with a clear contract. It can be tested in isolation,
injected wherever needed, and composed with other filters.

Every new protected route goes into `protectedRoutes()`. Every public route goes into `publicRoutes()`. The security
rule is co-located with the route definition. There is no second place to update.

Want role-based access? Add an `adminRoutes()` builder with a stricter filter. The pattern is that flexible.

## What You Give Up

To be fair, stepping outside Spring Security conventions has a cost:

- No `@PreAuthorize` or method-level security annotations
- No automatic integration with Spring Security's `ReactiveSecurityContextHolder` across the whole call chain
- Less familiar to developers who expect the standard setup

For teams that already rely on `@PreAuthorize` heavily, the migration cost would be real. But for greenfield WebFlux
projects those annotations were never in play — so there's nothing to give up.

## The Conclusion

Spring Security is a powerful framework solving real problems, but the problems it's best at solving are the ones
created by the annotation-driven MVC model. When you switch to functional routing, the router becomes your single source
of truth for what routes exist and who can access them. Splitting that across a `SecurityWebFilterChain` feels like
fighting the architecture rather than working with it.

Keep your security where your routes are. It's simpler, it's explicit, and when something breaks at 2am you'll know
exactly where to look.
