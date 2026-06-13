# CLAUDE.md — retorch-st-socialnetwork

This file gives Claude Code the context needed to work in this repository without re-deriving it each session.

## Project purpose

End-to-end test suite for the [DeathStarBench Social Network](https://github.com/augustocristian/docker-socialnetwork) microservices application, orchestrated with the [RETORCH](https://github.com/giis-uniovi/retorch) framework. The project contains two complementary test suites:

- **Frontend (E2E/browser)** — Selenium WebDriver tests that drive the HTML/Bootstrap UI through Chrome.
- **API** — HTTP-level tests that call the REST endpoints directly through the Nginx gateway.

Both suites share the same RETORCH resource model and run under the same CI pipeline.

---

## SUT architecture

The SUT is vendored in [`sut/`](sut/) (a copy of [`docker-socialnetwork`](https://github.com/augustocristian/docker-socialnetwork), with local bug fixes applied — see `sut/CLAUDE.md`). It is a Thrift-RPC microservice application fronted by an **OpenResty/Nginx gateway** (`nginx-thrift`, port 8080) whose Lua scripts translate HTTP requests into Thrift calls. The root [`docker-compose.yml`](docker-compose.yml) runs the prebuilt `deathstarbench/social-network-microservices:latest` image for the Thrift services and mounts `sut/config`, `sut/nginx-web-server`, `sut/gen-lua` and `sut/docker/openresty-thrift/lua-thrift` into the `nginx-thrift` container. The deploy scripts start it and wait up to 300 s for the gateway to serve the DeathStar UI. **Default local URL:** `http://localhost:8080`.

### Microservice topology

| Service | Role | Backing store(s) |
|---|---|---|
| `nginx-thrift` | API gateway + static UI (Lua → Thrift) | — (mounts `nginx-web-server/`, `gen-lua/`) |
| `user-service` | register / login / JWT issuance | `user-mongodb`, `user-memcached` |
| `social-graph-service` | follow / unfollow / followers / followees | `social-graph-mongodb`, `social-graph-redis` |
| `compose-post-service` | orchestrates post creation + fan-out | (calls other services; no own store) |
| `post-storage-service` | stores post documents | `post-storage-mongodb`, `post-storage-memcached` |
| `user-timeline-service` | a user's own posts | `user-timeline-mongodb`, `user-timeline-redis` |
| `home-timeline-service` | feed of followed users' posts | `home-timeline-redis` |
| `unique-id-service` | snowflake post-id generation | stateless |
| `text-service` | text processing (URLs, mentions hand-off) | stateless |
| `user-mention-service` | resolves `@username` mentions to user ids | stateless |
| `url-shorten-service` | shortens URLs in post text | `url-shorten-mongodb`, `url-shorten-memcached` |
| `media-service` / `media-frontend` (8081) | media upload/serve | `media-mongodb`, `media-memcached` |
| `jaeger-agent` (16686) | tracing | — |

All Thrift services listen on port 9090 internally and connect on the Docker network; ports are largely un-exposed except the gateway (8080), media-frontend (8081) and Jaeger UI (16686).

### Core data flows

- **Register** → `user-service` writes the user to `user-mongodb`.
- **Login** → `user-service` verifies credentials, returns a JWT; the gateway sets it as the `login_token` cookie and redirects to `main.html`.
- **Follow / unfollow** → `social-graph-service` updates `social-graph-mongodb` and caches the result set in `social-graph-redis` via `ZADD`.
- **Compose post** → `compose-post-service` fans out: `unique-id` (post id) → `text`/`url-shorten`/`user-mention` (enrichment) → `post-storage` (persist) → `user-timeline` (author's timeline) → `home-timeline` (each follower's feed). The home-timeline write reads the author's followers from `social-graph-service`.
- **Read user-timeline** → `user-timeline-service` returns the author's own posts.
- **Read home-timeline** → `home-timeline-service` returns the fan-out feed of the users you follow (never your own posts).

### Endpoint catalog

Two route trees exist. `/api/*` is the **browser UI** surface (JWT-cookie auth, redirects to HTML); `/wrk2-api/*` is the **unauthenticated load-test** surface that takes `user_id` directly — **the API test suite uses `/wrk2-api/*`** for post/timeline operations and `/api/*` for user/social-graph operations.

| Route | Method | Auth | Key params / behaviour |
|---|---|---|---|
| `/api/user/register` | POST form | none | `first_name,last_name,username,password`; 400 on missing field; 302→index.html |
| `/api/user/login` | POST form | none | `username,password`; sets `login_token`; 302→main.html; **wrong password → 500, no cookie** |
| `/api/user/follow` | POST form | none¹ | `user_name`+`followee_name` or `user_id`+`followee_id`; 302→contact.html |
| `/api/user/unfollow` | POST form | none¹ | same params; 302→contact.html (**see unfollow fix below**) |
| `/api/user/get_follower` | GET | JWT cookie | reads requester id from JWT; `[{follower_id}]`; **401 without cookie** |
| `/api/user/get_followee` | GET | JWT cookie | reads requester id from JWT; `[{followee_id}]`; **401 without cookie** |
| `/api/post/compose` | POST form | JWT cookie | `post_type,text[,media_*]`; user id/name taken from JWT |
| `/api/user-timeline/read` | GET | JWT cookie | `start,stop` |
| `/api/home-timeline/read` | GET | JWT cookie | `start,stop` |
| `/wrk2-api/post/compose` | POST form | none | `username,user_id,text,media_ids,media_types,post_type` |
| `/wrk2-api/user-timeline/read` | GET | none | `user_id,start,stop` |
| `/wrk2-api/home-timeline/read` | GET | none | `user_id,start,stop` |

¹ `follow`/`unfollow` have their JWT check commented out in the Lua, so they accept `user_name`/`followee_name` without a session.

### HTML pages (`nginx-web-server/pages/`)

| Page | Title | Key element ids | Timeline call / notes |
|---|---|---|---|
| `index.html` | login | form `name=username/password`, link "Sign Up" | login form posts to `api/user/login` |
| `signup.html` | signup | `name=first_name/last_name/username/password`, link "Login" | posts to `api/user/register` |
| `main.html` | DeathStar | `#show-post`, `#compose` (CSS `display:none`), `#post-content`, `#create-post`, `#card-block` | `showTimeline("home-timeline")` |
| `profile.html` | Profile | `#post-content`, `#card-block`, `#mentioned_user` | `showTimeline("user-timeline")` (own posts) |
| `contact.html` | Contact | follow & unfollow forms (duplicate `id="follow-form"`/`id="followee-name"`), `#follower-list`, `#followee-list` | `get-follower.js`, `get-followee.js` populate `.follower-id`/`.followee-id` |

### Known SUT bugs & quirks (each handled in the tests)

- **Empty-`ZADD` 500.** `social-graph-service` caches follower/followee lookups with Redis `ZADD`; when the set is **empty** Redis rejects the command and the call returns HTTP 500. This affects (a) **compose** for an author with no followers (the home-timeline fan-out calls `GetFollowers`), and (b) **`get_follower`/`get_followee`** when the requester has none. Tests always arrange ≥1 element: compose tests give the author a follower; the unfollow test follows two users and only unfollows one.
- **Unfollow file-download bug — FIXED in this repo.** Upstream `unfollow.lua` did not redirect on success, so the response fell through with `Content-Type: application/octet-stream`, making the browser treat the form submit as a **file download (save-file dialog)** and the unfollow never applied via the UI. Fixed by appending `ngx.redirect("../../contact.html")` (mirroring `follow.lua`) in `sut/nginx-web-server/lua-scripts/api/user/unfollow.lua`. Requires an `nginx-thrift` restart to reload (OpenResty caches Lua).
- **Home-timeline is asynchronous & excludes own posts.** A composed post reaches the author's **user-timeline** immediately but appears in followers' **home-timeline** only after fan-out. The browser post test therefore verifies via `profile.html` (user-timeline), and the API home-timeline test polls.
- **Empty home-timeline returns `{}`** (a JSON object, not `[]`); `BaseApiClass.getJsonArray()` tolerates this.
- **Snowflake user ids** are 64-bit (e.g. `1199044181654814720`) — exact in Java `long`; the `/api/*` Lua uses Lua's double-based `tonumber` consistently for reads and writes, so it stays self-consistent.

> The root `docker-compose.yml` is an adaptation of `sut/docker-compose.yml` (prebuilt images, `${TJOB_NAME}`-prefixed container names, config/Lua/gen-lua mounted from `sut/`). The `WriteHomeTimelineService` binary does **not** exist in the `deathstarbench/social-network-microservices:latest` image; home-timeline fan-out happens **compose-post-service → home-timeline-service → home-timeline-redis** directly.

---

## Repository layout

```
src/test/java/giis/socialnetwork/e2e/functional/
├── common/
│   ├── BaseApiClass.java        # HTTP base (form POST, cookie store, JWT parse, LaxRedirectStrategy, fixtures)
│   ├── BaseLoggedClass.java     # Selenium/Selema base (browser lifecycle, SUT url)
│   └── ElementNotFoundException.java
├── pages/                       # Page Object Model
│   ├── BasePage.java            # abstract: driver/waiter/sutUrl + navigate/fill/click/text/isDisplayed/isPresent
│   ├── LoginPage.java           # index.html
│   ├── SignupPage.java          # signup.html
│   ├── MainPage.java            # main.html (+ openProfile, compose) ; ContactPage entry
│   └── ContactPage.java         # contact.html (follow/unfollow, followee count)
├── tests/
│   ├── TestNavigation.java      # page structure + navbar links
│   ├── TestLogin.java           # register + login via forms
│   ├── TestPosts.java           # compose post → appears in profile (user-timeline)
│   ├── TestSocialGraph.java     # follow two / unfollow one via Contact page
│   └── api/
│       ├── TestApiUsers.java        # register (+400), login (+wrong-password)
│       ├── TestApiPosts.java        # compose, read user-timeline, @mention extraction, 400
│       ├── TestApiSocialGraph.java  # follow, unfollow, get_follower, get_followee
│       └── TestApiTimeline.java     # home-timeline array, eventual-consistency propagation, 400
└── utils/
    ├── Waiter.java              # explicit waits (10 s default, 30 s nav) + post-text refresh-retry
    └── Click.java               # safe click with JS fallback
```

The page objects all extend `BasePage`; there is **no** `Navigation` helper (removed — page `open()` covers navigation).

---

## Key dependencies

| Dependency | Purpose |
|---|---|
| JUnit 5 (Jupiter) | Test runner |
| Selenium WebDriver 4.x | Browser automation |
| Selema | Browser lifecycle manager |
| Apache HttpClient 4.5.14 | API HTTP client (form POST, cookie store, `LaxRedirectStrategy`) |
| Gson 2.14.0 | JSON / JWT payload parsing |
| RETORCH annotations | `@AccessMode` resource declarations for scheduling |
| Log4j2 + SLF4J | Per-TJob structured logging |

---

## RETORCH resource model

Each test declares the resources it touches with `@AccessMode`; declarations are kept **minimal and accurate** (a read-only or no-op test must not claim READWRITE on a resource it never mutates) so RETORCH can maximise safe parallelism.

| Resource ID | Represents | Typical access |
|---|---|---|
| `frontend` | Nginx HTML pages | `READONLY, concurrency=10, sharing=true` |
| `web-browser` | Chrome instance | `READWRITE, concurrency=1, sharing=false` |
| `user` | accounts in user-mongodb | `READWRITE` (register/login) |
| `post` | posts in post-storage-mongodb | `READWRITE` (compose) / `READONLY` (reads) |
| `social-graph` | follow relationships | `READWRITE` (follow/unfollow) / `READONLY` (lists) |
| `home-timeline` | home feed (redis) | `READONLY, concurrency=10, sharing=true` |
| `user-timeline` | user timeline (mongodb) | `READONLY, concurrency=10, sharing=true` |

**API tests never use `web-browser`/`frontend`.**

---

## Test inventory (24 tests)

**API (`tests/api/`, extend `BaseApiClass`):**
- `TestApiUsers` — register→200; register missing field→400; login sets JWT; wrong password sets no cookie.
- `TestApiPosts` — compose→200 (author given a follower to dodge empty-`ZADD`); read user-timeline; `@mention` populates `user_mentions`; missing-args→400.
- `TestApiSocialGraph` — follow→200; `get_follower` contains follower; `get_followee` contains followee; unfollow removes one of two followees.
- `TestApiTimeline` — home-timeline returns array; followed user's post propagates (polled); missing-args→400.

**Browser (`tests/`, extend `BaseLoggedClass`):**
- `TestLogin` — signup form registers; login form reaches the feed.
- `TestNavigation` — login/signup structure; navbar links; contact sections.
- `TestPosts` — compose form visible; composed post appears in profile timeline.
- `TestSocialGraph` — follow two users then unfollow one, verified via the Contact page followee count.

---

## Configuration

### `src/test/resources/test.properties`
```properties
BROWSER_USER=CHROME
LOCALHOST_URL=http://localhost:8080
```
Read by both base classes; in CI `-DSUT_URL=…` overrides it.

### `pom.xml` — per-TJob build directory
```xml
<directory>${project.basedir}/target/${TJOB_NAME}</directory>
```
Isolates each TJob's compiled classes/reports; the `local-execution` profile defaults `TJOB_NAME=local` locally. Log files go to `target/testlogs/log${sys:TJOB_NAME:-testinglocal}-test.log`.

---

## Running tests

```bash
./deploy-local.ps1      # Windows  (./deploy-local.sh on Linux/macOS) — start the SUT first
mvn test                # full suite
mvn test -Dtest=TestApiSocialGraph     # one class
```
CI runs per-method via `.retorch/scripts/tjoblifecycles/tjob-testexecution.sh <TJOB> <STAGE> <SUT_URL> "<Class#method>"`.

> After editing any SUT Lua script under `sut/nginx-web-server/lua-scripts/`, restart the gateway so OpenResty reloads it:
> `docker restart default-nginx-thrift` (container name is `${TJOB_NAME}-nginx-thrift`; `TJOB_NAME=default` for `deploy-local.*`).

---

## API test helpers (`BaseApiClass`)

- **HTTP:** `get`, `getStatus`, `postForm`, `postFormStatus` (follows POST redirects via `LaxRedirectStrategy`), `getJsonObject`, `getJsonArray` (tolerates `{}`).
- **URL builders:** `userUrl`, `wrk2PostUrl`, `wrk2UserTimelineUrl`, `wrk2HomeTimelineUrl`.
- **Payloads:** `registerPayload`, `loginPayload`, `composePostPayload`, `followPayload`, `unfollowPayload`.
- **Fixtures:** `registerUser`, `loginUser` (returns `user_id` from JWT), `loginSetsToken` (boolean, no throw — for auth-failure assertions), `createUser`, `createUserWithName` (`{username,userId,password}`), `composePost`, `followUser`, `unfollowUser`, `getFollowers`, `getFollowees`.
- **Assertions:** `containsByField(array, field, expected)` for follower/followee/mention lookups.

## Page objects (`BasePage`)

`BasePage` holds `driver/waiter/sutUrl` and the shared helpers: `navigate(path)`, `fill(by, value)` (waits for visibility, retries once on a stale-node `WebDriverException` during page transitions), `click(by)` (waits for clickable, then `Click` with JS fallback), `text(by)`, `isDisplayed(by)`, `isPresent(by)` (DOM presence — use for zero-height containers like `#follower-list`). Concrete pages add only locators + page-specific behaviour.

---

## Code style conventions (for this project)

- **No comments** unless the WHY is non-obvious (constraint, workaround, invariant).
- **`@DisplayName`** is a human-readable sentence, not the method name.
- **Test data isolation:** each test creates its own user(s) with a timestamp-based unique suffix; usernames are lower-cased and stripped to `[a-z0-9]`.
- **`@AccessMode`** declarations must match the resources actually accessed (no over-claiming).
- **No `Thread.sleep`** — use `LockSupport.parkNanos` for polling waits (Sonar `java:S2925`) and `Waiter`/`ExpectedConditions` for UI waits.
- **Logger:** use the inherited `log`; never instantiate a new one in a test.
- Use `Assertions.assertAll` when checking several facets of one outcome.
