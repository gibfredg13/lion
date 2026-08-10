# Docker Deployment Guide - Container-First Architecture

## Philosophy

**Everything runs in containers.** No local setup required beyond Docker.

```
User Machine
├── Docker Desktop / Docker Engine
└── docker-compose

Result: Complete working application in seconds
```

## Quick Start

### 1. Start All Services
```bash
docker-compose up -d
```

This starts:
- **hackmerlin-app** (Port 8080)
  - Spring Boot backend
  - React frontend
  - SQLite database
  - User authentication
  - Admin dashboard
  - Real-time monitoring

- **hackmerlin-ollama** (Port 11434)
  - Local Ollama LLM service
  - Pulls llama2 model on first startup

### 2. Access Application
```
http://localhost:8080
```

- Registration page for new users
- Login for existing users
- Game interface
- Admin dashboard at `/admin`

### 3. Stop Services
```bash
docker-compose down
```

## Architecture

### Services in docker-compose.yml

#### hackmerlin-app
```yaml
Service: hackmerlin-app
Container: hackmerlin-app
Port: 8080:8080
Volumes: ./data:/data (SQLite database)
Network: hackmerlin-network
Health Check: HTTP GET /
Restart: Unless Stopped
```

#### hackmerlin-ollama
```yaml
Service: ollama
Container: hackmerlin-ollama
Port: 11434:11434
Volumes: ollama_data:/root/.ollama (models cache)
Network: hackmerlin-network
Restart: Unless Stopped
```

### Network

Both containers communicate via `hackmerlin-network` bridge:
- hackmerlin-app → ollama (http://ollama:11434)
- Isolated from host network except specified ports

### Volumes

```yaml
./data/                    # SQLite database (persistent)
ollama_data/               # Ollama models cache (persistent)
```

Data persists across container restarts.

## Configuration

Set environment variables via `.env` file or `docker-compose.yml`:

### LLM Provider Selection

#### Ollama (Default - Local)
```bash
# .env file
MERLIN_LLM_PROVIDER=ollama
OLLAMA_ENDPOINT=http://ollama:11434
OLLAMA_MODEL=llama2
```

Already configured in docker-compose.yml. Just run:
```bash
docker-compose up -d
```

#### Azure OpenAI
```bash
# .env file
MERLIN_LLM_PROVIDER=azure
AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
AZURE_OPENAI_KEY=your-api-key
AZURE_OPENAI_DEPLOYMENT=your-deployment-name
```

#### Google Gemini
```bash
# .env file
MERLIN_LLM_PROVIDER=gemini
GEMINI_API_KEY=your-gemini-api-key
```

### Database Configuration

```bash
# .env file (optional, defaults to SQLite)
SPRING_DATASOURCE_URL=jdbc:sqlite:./data/hackmerlin.db
SPRING_JPA_HIBERNATE_DDL_AUTO=update

# For PostgreSQL (production)
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/hackmerlin
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=password
```

### Session Configuration

```bash
# .env file
SERVER_SERVLET_SESSION_TIMEOUT=30m
SPRING_SESSION_JDBC_TABLE_NAME=spring_session
```

## Common Tasks

### View Logs

All services:
```bash
docker-compose logs -f
```

Only app:
```bash
docker-compose logs -f hackmerlin-app
```

Only Ollama:
```bash
docker-compose logs -f ollama
```

### Access Database

```bash
# Enter app container
docker exec -it hackmerlin-app bash

# Query SQLite
sqlite3 /data/hackmerlin.db

# Example queries
SELECT * FROM users;
SELECT * FROM detected_attack;
.tables
.quit
```

### Rebuild Image

If code changes:
```bash
docker-compose build --no-cache
docker-compose up -d
```

### Clean Everything

```bash
# Stop containers
docker-compose down

# Remove volumes (WARNING: deletes data)
docker-compose down -v

# Remove images
docker rmi hackmerlin.io:latest ollama/ollama:latest
```

### Monitor Resources

```bash
# Docker stats
docker stats

# View container details
docker inspect hackmerlin-app
```

## Troubleshooting

### Application won't start

Check logs:
```bash
docker-compose logs hackmerlin-app
```

Common issues:
- Port 8080 already in use: `lsof -i :8080`
- Database permissions: `chmod 777 ./data`
- Out of disk space: `docker system prune -a`

### Ollama service won't start

```bash
docker-compose logs ollama
```

Common issues:
- Model download timeout: Increase health check timeout
- Out of memory: `docker stats` to check
- Network issues: `docker network inspect hackmerlin-network`

### Slow response times

Check container resources:
```bash
docker stats hackmerlin-app
```

If high CPU/memory:
- Increase Docker Desktop resources
- Reduce concurrent users
- Switch to faster LLM model

### Data not persisting

Check volumes:
```bash
docker volume ls
docker volume inspect hackmerlin_ollama_data
```

Verify mounting:
```bash
docker inspect hackmerlin-app | grep -A 5 Mounts
```

## Production Deployment

For production, modify docker-compose.yml:

### Scale Horizontally

```yaml
version: '3.8'
services:
  # Load balancer
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
    depends_on:
      - app1
      - app2
      - app3

  # Multiple app instances
  app1:
    build: .
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/hackmerlin
    depends_on:
      - postgres

  app2:
    build: .
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/hackmerlin
    depends_on:
      - postgres

  app3:
    build: .
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/hackmerlin
    depends_on:
      - postgres

  # Shared database
  postgres:
    image: postgres:15
    volumes:
      - postgres_data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: hackmerlin
      POSTGRES_PASSWORD: secure-password

  # Shared Ollama or use managed service
  ollama:
    image: ollama/ollama:latest
    volumes:
      - ollama_data:/root/.ollama

volumes:
  postgres_data:
  ollama_data:
```

### Use Managed Services

For enterprises:
- **Database**: Use AWS RDS, Azure SQL, or Google Cloud SQL
- **LLM**: Use managed Azure OpenAI or Google Vertex AI
- **Container Orchestration**: Use Kubernetes, ECS, or App Service

Remove those services from docker-compose.yml and update environment variables.

### SSL/TLS

Add reverse proxy (nginx):
```yaml
nginx:
  image: nginx:alpine
  ports:
    - "443:443"
  volumes:
    - ./nginx.conf:/etc/nginx/nginx.conf
    - /etc/letsencrypt:/etc/letsencrypt
  depends_on:
    - hackmerlin-app
```

## Monitoring & Observability

### Container Health

Docker health check (built-in):
```bash
docker ps
# HEALTH column shows: healthy/unhealthy/starting
```

### Application Metrics

Prometheus endpoint available at:
```
http://localhost:8080/actuator/prometheus
```

### Structured Logging

All logs output to stdout (container logs):
```bash
docker-compose logs --timestamps --follow
```

Parse with ELK, Loki, or similar.

## Best Practices

✅ **DO:**
- Always use `docker-compose` for local development
- Mount `./data` volume for persistent storage
- Use `.env` file for secrets (never commit)
- Set resource limits in docker-compose.yml
- Use health checks
- Monitor logs regularly

❌ **DON'T:**
- Run containers directly (always use compose)
- Commit secrets to docker-compose.yml
- Disable restart policies
- Ignore health check failures
- Run multiple compose projects on same ports

## Deployment Checklist

- [ ] Docker and Docker Compose installed
- [ ] `.env` file created with secrets
- [ ] Port 8080 available
- [ ] Disk space for data volume (20 GB+ recommended)
- [ ] Network access configured
- [ ] Health checks passing
- [ ] Data volume mounted correctly
- [ ] Logs monitored
- [ ] Backup strategy in place
- [ ] Monitoring configured

## Support

See main documentation:
- `docs/DEPLOY_WITH_AUTH.md` - Deployment reference
- `docs/DOCKER_GUIDE.md` - Docker specifics
- `README.md` - Quick start

