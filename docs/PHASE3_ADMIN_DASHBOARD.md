# Phase 3: Admin Dashboard Complete

## Architecture
- Standalone React/Vite/TypeScript frontend.
- Runs on port 3001, separate from the main Spring Boot app (8080).
- Fully Dockerized and orchestrated using docker-compose.
- Integrates with the backend using session cookies via `/api` proxy.

## Key Features
- Complete Dark Mode/Orange Theme matching event branding.
- Live prompt feed showing ongoing player interactions.
- User management and tracking (levels, tokens, activity).
- Level toggling capability (enable/disable levels globally).
- Real-time Leaderboard with reset capabilities.
- Health monitoring of LLM endpoints (DGX Spark).

## How to Run
- `docker-compose up -d --build` to run everything together.
- Access the Admin Dashboard via `http://localhost:3001`.
- Access the main game via `http://localhost:8080`.
