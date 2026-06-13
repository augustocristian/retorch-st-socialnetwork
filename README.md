[![Build Status](https://github.com/giis-uniovi/retorch-st-socialnetwork/actions/workflows/test.yml/badge.svg)](https://github.com/giis-uniovi/retorch-st-socialnetwork/actions)

# RETORCH Social Network End-to-End Test Suite

End-to-End test suite for the [DeathStarBench Social Network](https://github.com/augustocristian/docker-socialnetwork) microservices application, used as a demonstrator of the [RETORCH Framework](https://github.com/giis-uniovi/retorch).

The Social Network is a distributed benchmark application based on [DeathStarBench](https://github.com/delimitrou/DeathStarBench), built with Thrift RPC, Nginx/OpenResty, MongoDB, Redis, Memcached and RabbitMQ — all running in Docker containers.

## Test suites

| Suite | Base class | What it tests |
|---|---|---|
| **API** | `BaseApiClass` | REST endpoints via Apache HttpClient (form POST + JSON response) |
| **Browser (E2E)** | `BaseLoggedClass` | Web UI via Selenium WebDriver (Chrome) |

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows / macOS) or Docker Engine (Linux)
- Git
- Java 8+, Maven 3.x

## Deployment

The SUT is vendored in the [`sut/`](sut/) directory of this repository. The deploy scripts create the `jenkins_network` Docker network, start all containers using the root `docker-compose.yml` (which mounts config, Lua scripts and generated Thrift bindings from `sut/`), and wait up to 300 seconds for the Nginx gateway to be ready.

### Windows (PowerShell)

```powershell
# Start the SUT on the default port (8080)
.\deploy-local.ps1

# Start on a custom port
.\deploy-local.ps1 -Port 9090

# Tear down all containers and volumes
.\deploy-local.ps1 -Down
```

### Linux / macOS

```bash
# Make the script executable (first time only)
chmod +x deploy-local.sh

# Start the SUT on the default port (8080)
./deploy-local.sh

# Start on a custom port
./deploy-local.sh --port 9090

# Tear down all containers and volumes
./deploy-local.sh --down
```

Once up, the SUT is accessible at `http://localhost:8080` (default).

## Running the tests

The SUT must be running before executing the tests.

```bash
# Run the full suite
mvn test

# Run a specific API test class
mvn test -Dtest=TestApiUsers
mvn test -Dtest=TestApiPosts
mvn test -Dtest=TestApiSocialGraph
mvn test -Dtest=TestApiTimeline

# Run a specific browser test class
mvn test -Dtest=TestLogin
mvn test -Dtest=TestNavigation
mvn test -Dtest=TestPosts
```

## CI deployment — Jenkins

The `Jenkinsfile` at the repository root defines the full pipeline used by the on-premises Jenkins instance.
It relies on the lifecycle scripts in `.retorch/scripts/` and the environment files in `.retorch/envfiles/`.
The GitHub Actions workflow (`.github/workflows/test.yml`) compiles the project; actual test execution is delegated to Jenkins via RETORCH orchestration.
