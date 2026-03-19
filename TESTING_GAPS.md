# Testing Gaps

Currently 3 test files exist covering ~5-10% of production code.

**Existing tests:**

- `AuthIntegrationTest` — register/login endpoints (happy path + validation)
- `TokenServiceTest` — all TokenService methods
- `UserServiceTest` — UserService register/login/logout/refreshToken

---

## Handlers (0% coverage)

| Handler        | Untested Methods                                                                |
|----------------|---------------------------------------------------------------------------------|
| `SiteHandler`  | `create`, `list`, `get`, `update`, `delete`                                     |
| `PostHandler`  | `create`, `list`, `get`, `update`, `delete`, `publish`, `unpublish`, `schedule` |
| `TagHandler`   | `list`, `delete`                                                                |
| `BlogsHandler` | `index`, `postList`, `post`, `rssRoot`, `rss`, `postListJson`                   |
| `AdminHandler` | `serve`, `logout`                                                               |

---

## Services

| Service        | Status                                                                                            |
|----------------|---------------------------------------------------------------------------------------------------|
| `UserService`  | ✅ unit tested                                                                                     |
| `TokenService` | ✅ unit tested                                                                                     |
| `PostService`  | ❌ `create`, `get`, `list`, `update`, `delete`, `publish`, `unpublish`, `schedule`, `generateSlug` |
| `SiteService`  | ❌ `create`, `list`, `findById`, `update`, `delete`                                                |
| `TagService`   | ❌ `list`, `delete`                                                                                |
| `BlogService`  | ❌ `listPublished`, `getBySlug`, `renderMarkdown`                                                  |

---

## Repositories (0% direct coverage)

All four repositories have zero tests.

- `UserRepository` — `findByUserId`, `findByEmail`, `create`, `saveRefreshToken`, `findRefreshToken`,
  `deleteRefreshToken`, `deleteExpiredTokens`
- `PostRepository` — `create`, `findById`, `findAllBySiteId`, `countBySiteId`, `update`, `publishScheduled`, `delete`,
  `createTranslation`, `updateTranslation`, `findTranslationsByPostId`, `findPublishedBySiteAndLang`,
  `countPublishedBySiteAndLang`, `findPublishedBySlug`, `incrementViewCount`
- `SiteRepository` — `findById`, `findAllByUserId`, `create`, `update`, `delete`, `findByDomain`, `existsByDomain`
- `TagRepository` — `findOrCreate`, `findBySiteId`, `findByPostId`, `assignToPost`, `replacePostTags`, `delete`

Notable high-risk gaps:

- `PostRepository.publishScheduled()` — bulk status transition
- `PostRepository.buildAdminQuery()` / `buildBlogQuery()` — dynamic query building with filters
- `SiteRepository.mapToSite()` — JSONB parsing with 3 type branches (String/ByteArray/Json)
- `TagRepository.replacePostTags()` — delete + re-insert atomicity
- `UserRepository.findRefreshToken()` — token lookup with expiry check

---

## Filters (0% coverage)

- `JwtAuthFilter` — JWT extraction, context injection, error on invalid/missing token
- `HostFilter` — domain resolution, `SiteContext` injection, landing page fallback
- `AdminExceptionFilter` — `UnauthorizedException` → redirect to login, `ApiException` → error page
- `BlogExceptionFilter` — exception → error page

---

## Schedulers (0% coverage)

- `PublishPostsScheduler` — promotes `SCHEDULED` → `PUBLISHED`
- `ExpiredTokenScheduler` — cleans up expired refresh tokens
- `SchedulerBase` — error recovery, interval behavior

---

## Auth Endpoints Missing from Integration Tests

- `POST /auth/logout` — cookie clearing, token invalidation
- `POST /auth/refresh` — token rotation flow
- Cookie flags (`HttpOnly`, `Secure`, `SameSite`) not asserted anywhere

---

## Utilities (0% coverage)

- `RssFeed.buildRss()` — XML generation and escaping
- `RequestValidator` — Jakarta validation integration
- `Extensions.isSecureContext()` — HTTPS detection logic (localhost/private IP exclusions)

---

## Missing Integration Test Scenarios

Cross-cutting flows with no test coverage:

- **User registration → site creation → post creation → publish → visible in blog**
- **Scheduled post → scheduler fires → post becomes published**
- **Multi-tenancy isolation** — user A cannot access user B's sites/posts
- **Multi-language** — post with both `en` and `es` translations, language-prefixed routes
- **Tag filtering** — create tags, filter posts by tag via REST and blog UI
- **RSS feed content** — feed reflects published posts, correct XML structure
- **Domain-based routing** — `HostFilter` resolves different domains to different sites
- **Post search** — full-text search via `list` endpoint
- **Pagination** — boundary conditions (page 0, last page, empty results)
- **Concurrent token refresh** — race on refresh token rotation
