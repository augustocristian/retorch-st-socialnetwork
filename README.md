[![Build Status](https://github.com/giis-uniovi/retorch-st-socialnetwork/actions/workflows/test.yml/badge.svg)](https://github.com/giis-uniovi/retorch-st-socialnetwork/actions)

# RETORCH Social Network End-to-End Test Suite

This repository contains an End-to-End Test suite for the
[DeathStarBench Social Network](https://github.com/augustocristian/docker-socialnetwork) microservices application,
used as a demonstrator of the [RETORCH Framework](https://github.com/giis-uniovi/retorch).

The Social Network is a benchmark application based on
[DeathStarBench](https://github.com/delimitrou/DeathStarBench) built as a distributed microservices
architecture using Thrift RPC, Nginx/OpenResty, MongoDB, Redis, Memcached and RabbitMQ,
all running in Docker containers.

## Test suites

| Suite | Base class | What it tests |
|---|---|---|
| **API** | `BaseApiClass` | REST endpoints via Apache HttpClient (form POST + JSON response) |
| **Browser (E2E)** | `BaseLoggedClass` | Web UI via Selenium WebDriver (Chrome) |

## Deployment instructions

### Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows / macOS) or Docker Engine (Linux)
- Git

The SUT is cloned automatically by the deploy scripts from
`https://github.com/augustocristian/docker-socialnetwork` if the `docker-socialnetwork/` directory
is not already present.

### Local deployment — Windows

```powershell
# Start the SUT on the default port (8080)
.\deploy-local.ps1

# Start on a custom port
.\deploy-local.ps1 -Port 9090

# Tear down all containers and volumes
.\deploy-local.ps1 -Down
```

### Local deployment — Linux / macOS

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

Both scripts handle all setup steps automatically: clone the SUT repository if needed,
create the `jenkins_network` Docker network, start the containers, and wait up to 300 seconds
for the Nginx gateway to serve the Social Network UI.

Once the SUT is up it is accessible at `http://localhost:<port>` (default `http://localhost:8080`).

### Running the tests

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=TestApiUsers
mvn test -Dtest=TestNavigation
```

### CI deployment — Jenkins

The `Jenkinsfile` at the repository root defines the full pipeline used by the on-premises Jenkins instance.
It relies on the lifecycle scripts located in `.retorch/scripts/` and the environment files in
`.retorch/envfiles/`. The GitHub Actions workflow (`.github/workflows/test.yml`) compiles the project;
the actual test execution is delegated to Jenkins.
