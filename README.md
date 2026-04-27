# TripMind

TripMind is a Spring Boot travel planning application that turns free-form chat into a structured trip plan with budget checks, flight and hotel estimates, weather context, trip advice, itinerary generation, and booking handoff links.

## What It Does

- Collects trip details through natural chat instead of a rigid form
- Separates exploratory travel chat from planning mode
- Clarifies missing or ambiguous fields like dates, trip type, and budget scope
- Generates a plan with budget, flights, hotels, weather, advisor notes, and itinerary
- Uses booking links to continue into external travel sites with trip context prefilled
- Supports graceful fallbacks when live provider data is weak or unavailable

## Tech Stack

- Java 21
- Spring Boot
- Spring MVC
- Spring Data JPA
- H2 database
- Redis Streams for workflow traces and agent events
- External provider integrations for places and weather

## Project Structure

- `src/main/java` - application, controller, service, workflow, and agent logic
- `src/main/resources/static/index.html` - frontend UI
- `src/main/resources/application.properties` - base app configuration
- `src/main/resources/application-docker.properties` - Docker runtime overrides
- `.env.example` - example environment variables
- `Dockerfile` - container image build
- `docker-compose.yml` - local app + Redis setup

## Run Locally

### Requirements

- Java 21
- Maven wrapper included in the repo

### Steps

1. Copy environment variables:

```bash
cp .env.example .env
```

2. Start the app:

```bash
./mvnw spring-boot:run
```

3. Open:

- App: `http://127.0.0.1:8080`
- H2 console: `http://127.0.0.1:8080/h2-console`

## Run With Docker

### Requirements

- Docker
- Docker Compose

### Steps

1. Copy environment variables:

```bash
cp .env.example .env
```

2. Build and start:

```bash
docker compose up --build
```

3. Open:

- App: `http://127.0.0.1:8080`

This setup runs:

- `tripmind-app` on port `8080`
- `tripmind-redis` on port `6379`

Application data is stored in:

- `./docker-data/app`
- `./docker-data/redis`

## Environment Variables

The app can run in rule-based mode with no paid model key. Add only the providers you want to use.

- `TRIPMIND_AI_PROVIDER`
- `OPENAI_API_KEY`
- `GROQ_API_KEY`
- `OPENROUTER_API_KEY`
- `XAI_API_KEY`
- `GEOAPIFY_API_KEY`
- `OPENTRIPMAP_API_KEY`
- `OPENWEATHER_API_KEY`

## Testing

Run the test suite with:

```bash
./mvnw test
```

## Suggested GitHub README Additions Later

- screenshots of the home screen and generated plan
- architecture diagram
- deployment link
- known limitations
- future improvements

## Current Limitations

- Flight and hotel values are planning estimates, not confirmed inventory
- Some itinerary quality depends on third-party place data quality
- Broad country-level destinations should be narrowed to a city for the best plans
- Advanced multi-city optimization is still limited

## Good Student Project Process

This project is in a realistic student-to-portfolio path:

1. build the app locally
2. test and harden real user chat flows
3. document it properly
4. containerize it with Docker
5. publish it on GitHub
6. deploy a demo
7. collect feedback
8. improve it iteratively
