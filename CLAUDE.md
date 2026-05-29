# CLAUDE.md — retorch-st-socialnetwork

This file gives Claude Code the context needed to work in this repository without re-deriving it each session.

## Project purpose

End-to-end test suite for the [DeathStarBench Social Network](https://github.com/augustocristian/docker-socialnetwork) microservices application, orchestrated with the [RETORCH](https://github.com/giis-uniovi/retorch) framework. The project contains two complementary test suites:

- **Frontend (E2E/browser)** — Selenium WebDriver tests that drive the HTML/Bootstrap UI through Chrome.
- **API** — HTTP-level tests that call the REST endpoints directly through the Nginx gateway.

Both suites share the same RETORCH resource model and run under the same CI pipeline.

---

## System Under Test (SUT)

The SUT lives in the `docker-socialnetwork/` directory (cloned from `https://github.com/augustocristian/docker-socialnetwork` if not already present). It is started by the deployment scripts at runtime:

```bash
# Linux/macOS
./deploy-local.sh

# Windows PowerShell
./deploy-local.ps1
```

The scripts clone the SUT if needed, create the `jenkins_network` Docker network, start all containers, and wait up to 300 seconds for the Nginx gateway to serve the DeathStar UI.

**Default local URL:** `http://localhost:8080`

### Microservices and Nginx routes

| Route prefix | Auth | Notes |
|---|---|---|
| `/api/user/register` | none | POST (form); redirects to index.html |
| `/api/user/login` | none | POST (form); sets `login_token` JWT cookie; redirects to main.html |
| `/api/user/follow` | none | POST (form); JWT check commented out in Lua |
| `/api/user/get_follower` | JWT cookie | GET; returns follower IDs |
| `/api/post/compose` | JWT cookie | POST (form); authenticated compose used by the browser UI |
| `/api/home-timeline/read` | JWT cookie | GET; browser UI version — reads user_id from JWT |
| `/api/user-timeline/read` | JWT cookie | GET; browser UI version — reads user_id from JWT |
| `/wrk2-api/post/compose` | none | POST (form); unauthenticated — used by API tests |
| `/wrk2-api/user-timeline/read` | none | GET; params: user_id, start, stop |
| `/wrk2-api/home-timeline/read` | none | GET; params: user_id, start, stop |

The home timeline is populated **asynchronously** via RabbitMQ → `write-home-timeline-service`. After `POST /wrk2-api/post/compose`, the post may not appear in the home timeline immediately.

### Known SUT limitation — ZADD with empty follower set

`social-graph-service::GetFollowers` does a Redis `ZADD` to cache the result after a MongoDB lookup. When the author has **no followers**, the member set is empty and Redis rejects the `ZADD` command, causing the entire compose-post Thrift call to return 500. The post IS still written to `post-storage` and `user-timeline` before the failure occurs.

**Workaround in tests:** every compose test ensures the author has at least one follower before composing (so the `ZADD` is non-empty). See `testComposePost` in `TestApiPosts` and `testComposePostAppearsInTimeline` in `TestPosts`.

### Missing services added to docker-socialnetwork/docker-compose.yml

The upstream `docker-compose.yml` lacked `compose-post-redis`, `write-home-timeline-rabbitmq`, and `write-home-timeline-service`. `compose-post-redis` and `write-home-timeline-rabbitmq` have been added and are running. Note: the `WriteHomeTimelineService` binary does not exist in the pre-built `deathstarbench/social-network-microservices:latest` image, so that service cannot be started.

---

## Repository layout

```
src/test/java/giis/socialnetwork/e2e/functional/
├── common/
│   ├── BaseApiClass.java        # HTTP base class (form POST, cookie store, JWT parsing, LaxRedirectStrategy)
│   ├── BaseLoggedClass.java     # Selenium base class (browser lifecycle, shared helpers)
│   └── ElementNotFoundException.java
├── pages/
│   ├── LoginPage.java           # index.html — login form
│   ├── SignupPage.java          # signup.html — registration form
│   ├── MainPage.java            # main.html — home timeline + compose (hidden by default, toggled by #show-post)
│   └── ContactPage.java        # contact.html — follow/unfollow, follower/followee lists
├── tests/
│   ├── TestNavigation.java      # Browser: page structure and navbar links
│   ├── TestLogin.java           # Browser: register + login flows via forms
│   ├── TestPosts.java           # Browser: compose post, verify in user-timeline (profile.html)
│   └── api/
│       ├── TestApiUsers.java    # API: register + login, JWT extraction
│       ├── TestApiPosts.java    # API: compose post, read user timeline
│       ├── TestApiSocialGraph.java  # API: follow user, get followers (JWT auth)
│       └── TestApiTimeline.java # API: home timeline with eventual consistency polling
└── utils/
    ├── Navigation.java          # Direct URL navigation helpers
    ├── Waiter.java              # Explicit wait helpers for Social Network pages
    └── Click.java               # Safe click with JS fallback
```

---

## Key dependencies

| Dependency | Purpose |
|---|---|
| JUnit 5 (Jupiter) | Test runner |
| Selenium WebDriver 4.x | Browser automation (frontend tests) |
| Selema | Browser lifecycle manager (wraps SeleManager) |
| Apache HttpClient 4.5.14 | HTTP client (API tests — form-encoded POST, cookie store, LaxRedirectStrategy) |
| Gson 2.14.0 | JSON parsing (API tests, JWT decoding) |
| RETORCH annotations | `@AccessMode` resource declarations for scheduling |
| Log4j2 + SLF4J | Structured logging with per-TJob log files |

---

## RETORCH resource model

Each test declares the resources it accesses with `@AccessMode`. RETORCH uses these to generate a parallel-safe Jenkinsfile.

| Resource ID | Represents | Typical access |
|---|---|---|
| `frontend` | Nginx HTML pages (read-only, high concurrency) | `READONLY, concurrency=10, sharing=true` |
| `web-browser` | Chrome browser instance | `READWRITE, concurrency=1, sharing=false` |
| `user` | User accounts in user-mongodb | `READWRITE` (register/login), concurrency=1 |
| `post` | Posts in post-storage-mongodb | `READWRITE` (compose), `READONLY` (timeline reads) |
| `social-graph` | Follow relationships in social-graph-mongodb | `READWRITE` (follow/unfollow), `READONLY` (get followers) |
| `home-timeline` | Home feed data in home-timeline-redis | `READONLY, concurrency=10, sharing=true` |
| `user-timeline` | User timeline data in user-timeline-mongodb | `READONLY, concurrency=10, sharing=true` |

**API tests do not use `web-browser` or `frontend`** — they interact directly with the HTTP layer.

---

## Configuration

### `src/test/resources/test.properties`
```properties
BROWSER_USER=CHROME
LOCALHOST_URL=http://localhost:8080
```
Both `BaseApiClass` and `BaseLoggedClass` read `LOCALHOST_URL` as the SUT base URL. In CI, `SUT_URL` is passed as a system property to override it.

### `src/test/resources/log4j2.xml`
Log files are written to `target/testlogs/log${sys:TJOB_NAME:-testinglocal}-test.log`. Each parallel TJob writes to a separate file.

### `pom.xml` — per-TJob build directory
```xml
<directory>${project.basedir}/target/${TJOB_NAME}</directory>
```
Each Maven invocation writes compiled classes and surefire reports under `target/{TJOB_NAME}/`, preventing parallel CI builds from corrupting each other. A Maven profile (`local-execution`) defaults `TJOB_NAME=local` when the property is absent.

---

## Running tests

### Local — full suite
```bash
# Start the SUT first
./deploy-local.sh        # Linux
./deploy-local.ps1       # Windows

# Run all tests
mvn test
```

### Local — specific class
```bash
mvn test -Dtest=TestApiUsers
mvn test -Dtest=TestNavigation
```

### CI (Jenkins)
Tests are executed by the RETORCH TJob lifecycle scripts:
```bash
.retorch/scripts/tjoblifecycles/tjob-testexecution.sh <TJOB_NAME> <STAGE> <SUT_URL> "<TestClass#method>"
```
which internally runs:
```bash
mvn test -Dtest="<TestClass#method>" -DTJOB_NAME="<TJOB_NAME>" -DSUT_URL="<SUT_URL>"
```

---

## Authentication model

The social network uses JWT-based session cookies (`login_token`). The `POST /api/user/login` endpoint sets this cookie via `Set-Cookie` and redirects the browser to `main.html`.

**API tests** use Apache HttpClient with `BasicCookieStore` and `LaxRedirectStrategy` (so POST redirects are followed and the final 200 response is captured). The JWT payload (base64url-decoded middle segment) contains `user_id` and `username`. `BaseApiClass.loginUser()` extracts `user_id` from the JWT payload without signature verification (the secret is `"secret"`).

**User IDs are large 64-bit snowflake integers** (e.g., `1167518873994911744`). Java `long` handles them exactly, but Lua's `tonumber()` (which uses IEEE-754 double precision) may round them. The `/api/*` browser-side endpoints use the rounded Lua value consistently for both reads and writes, so they remain self-consistent.

---

## Form-encoded requests (not JSON)

All Social Network API endpoints accept `application/x-www-form-urlencoded` POST bodies, not JSON. `BaseApiClass` uses `UrlEncodedFormEntity` and `BasicNameValuePair` from Apache HttpClient for all mutations. Read operations use plain `HttpGet`.

---

## Home timeline eventual consistency

After `POST /wrk2-api/post/compose`, the home-timeline-service is updated **asynchronously** via RabbitMQ and `write-home-timeline-service`. `TestApiTimeline.testHomeTimelineShowsFollowedUserPost` polls `GET /wrk2-api/home-timeline/read` (up to `HOME_TIMELINE_TIMEOUT_MS = 15 000 ms`, every `HOME_TIMELINE_POLL_MS = 500 ms`) using `LockSupport.parkNanos` — never `Thread.sleep`, which triggers Sonar rule `java:S2925`.

The browser test `TestPosts.testComposePostAppearsInTimeline` navigates to `profile.html` (user-timeline) after composing, not back to `main.html` (home-timeline). The home-timeline only shows posts from followed users; the author's own composed post only appears in their user-timeline.

---

## Empty home-timeline response

`GET /wrk2-api/home-timeline/read` returns `{}` (empty JSON object, not `[]`) when the user has no posts in their home timeline. `BaseApiClass.getJsonArray()` handles this by returning an empty `JsonArray` when the response is not a JSON array.

---

## UI — compose form is hidden by default

The compose section on `main.html` has `display: none` in `main.css` and is toggled by clicking the `#show-post` nav link. `MainPage.composePost()` and `MainPage.isComposeFormVisible()` both click `#show-post` first before interacting with the form.

---

## UI — contact page follower list

The `#follower-list` and `#followee-list` container divs on `contact.html` have zero height when empty (their `.post-card` children are `display: none`). `ContactPage.isFollowerListDisplayed()` and `isFolloweeListDisplayed()` check for DOM presence (`!findElements().isEmpty()`) rather than WebDriver visibility, which would return false for zero-height elements. `Waiter.waitForContactPage()` waits for `#followee-name` (the follow form input, always visible) instead of `#follower-list`.

---

## User uniqueness

Each test creates users with a timestamp-based username suffix (e.g., `reg1748430000000`) to avoid conflicts across repeated or parallel runs. Usernames are lower-cased and stripped of non-alphanumeric characters to satisfy the social network's constraints.

---

## Parallel build isolation

Multiple TJobs run concurrently in CI, each calling `mvn test` in the same workspace. The `<directory>` override in `pom.xml` plus per-TJob log files prevent classpath and log file collisions.

---

## API test helpers (`BaseApiClass`)

### HTTP verbs
- `get(url)` — GET, returns response body as `String`
- `getStatus(url)` — GET, returns HTTP status code
- `postForm(url, params)` — form-encoded POST, returns response body
- `postFormStatus(url, params)` — form-encoded POST, returns HTTP status code (follows POST redirects)

### URL builders
- `userUrl(path)` → `sutUrl + "/api/user" + path`
- `wrk2PostUrl(path)` → `sutUrl + "/wrk2-api/post" + path`
- `wrk2UserTimelineUrl(path)` → `sutUrl + "/wrk2-api/user-timeline" + path`
- `wrk2HomeTimelineUrl(path)` → `sutUrl + "/wrk2-api/home-timeline" + path`

### Payload builders (return `List<NameValuePair>`)
- `registerPayload(firstName, lastName, username, password)`
- `loginPayload(username, password)`
- `composePostPayload(username, userId, text)` — media_ids/types default to `[]`, post_type to `0`
- `followPayload(userName, followeeName)`

### Fixture helpers
- `registerUser(firstName, lastName, username, password)` — returns HTTP status
- `loginUser(username, password)` — clears cookie store, logs in, returns `user_id` from JWT
- `createUser(label)` — derives all fields from `label + unique()`, registers + logs in, returns `user_id`
- `createUserWithName(label)` — same but returns `String[]{username, userId, password}`
- `composePost(username, userId, text)` — returns HTTP status
- `followUser(userName, followeeName)` — returns HTTP status

---

## Code style conventions (for this project)

- **No comments** unless the WHY is non-obvious (constraint, workaround, invariant).
- **`@DisplayName`** must be a human-readable sentence, not the method name.
- **Test data isolation**: each test creates its own user with a timestamp-based unique suffix.
- **`BaseApiClass` helpers**: use `createUser()` / `createUserWithName()` for setup. Do not duplicate form-param construction inline.
- **Assertions**: use `Assertions.assertAll` when checking multiple fields of the same object.
- **Logger**: always use the inherited `log` field; never instantiate a new logger in a test class.
- **No `Thread.sleep`**: use `LockSupport.parkNanos` for polling waits to avoid Sonar rule `java:S2925`.
