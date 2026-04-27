# TripMind

TripMind is a Spring Boot travel planning project that turns a chat-style trip request into a structured travel plan. Instead of only giving generic suggestions, it tries to check feasibility first and then build a usable plan with budget context, flight and hotel estimates, weather guidance, advisor notes, and a day-by-day itinerary.

## Features

- Chat-based trip planning instead of a fixed form
- Separation between exploratory travel chat and actual planning mode
- Clarification of missing or ambiguous inputs like dates, trip type, and budget scope
- Budget verdict with estimated category-wise breakdown
- Flight and hotel planning with booking handoff links
- Weather context, packing guidance, and advisor notes
- Day-by-day itinerary generation
- Fallback handling when live or provider data is weak

## Tech Stack

- Java 21
- Spring Boot
- Spring MVC
- Spring Data JPA
- H2 database
- Redis Streams for workflow traces and agent events
- External provider integrations for places and weather

## Project Layout

- `src/main/java` - application, controller, service, workflow, and agent logic
- `src/main/resources/static/index.html` - frontend UI
- `src/main/resources/application.properties` - base app configuration
- `src/main/resources/application-docker.properties` - Docker runtime overrides
- `.env.example` - example environment variables
- `Dockerfile` - container image build
- `docker-compose.yml` - local app + Redis setup

## Local Setup

### Prerequisites

- Java 21
- Maven wrapper included in the repo

### Run the app

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

### Prerequisites

- Docker
- Docker Compose

### Start the containers

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

Persistent data is stored in:

- `./docker-data/app`
- `./docker-data/redis`

## Environment Variables

The project can run in rule-based mode without a paid model key. Add only the providers you want to use.

- `TRIPMIND_AI_PROVIDER`
- `OPENAI_API_KEY`
- `GROQ_API_KEY`
- `OPENROUTER_API_KEY`
- `XAI_API_KEY`
- `GEOAPIFY_API_KEY`
- `OPENTRIPMAP_API_KEY`
- `OPENWEATHER_API_KEY`

## Testing

To run the test suite:

```bash
./mvnw test
```

## Current Limitations

- Flight and hotel values are planning estimates, not confirmed inventory
- Some itinerary quality depends on third-party place data quality
- Broad country-level destinations should be narrowed to a city for the best plans
- Advanced multi-city optimization is still limited

## Next Improvements

- Add screenshots of the main chat flow and generated plan
- Publish a hosted demo link
- Improve city-level grounding for broad country requests
- Expand multi-city and travel-time validation
- Add stronger save/share and plan history features
