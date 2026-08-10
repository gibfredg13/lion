# 🐳 HackMerlin Docker Setup Guide

## Quick Start (Docker Compose)

The easiest way - everything runs in containers!

### Prerequisites

```bash
# Install Docker Desktop
# Mac: https://docs.docker.com/desktop/install/mac-install/
# Windows: https://docs.docker.com/desktop/install/windows-install/
# Linux: https://docs.docker.com/engine/install/

# Verify installation
docker --version
docker-compose --version
```

---

## 🚀 Option 1: Docker Compose (Recommended)

Runs HackMerlin + Ollama in containers with one command!

### Setup

```bash
cd /Users/ir45jr/Developer/Merlin/hackmerlin.io

# Build and start all services
docker-compose up -d

# Wait for services to start (30-60 seconds)
# Check status:
docker-compose ps

# View logs:
docker-compose logs -f hackmerlin
```

### Access Application

Once services are up (check `docker-compose ps`):

- **Challenge:** http://localhost:8080
- **Admin:** http://localhost:8080/admin
- **Breach Monitor:** http://localhost:8080/admin/breaches
- **Leaderboard:** http://localhost:8080/leaderboard

### Stop Services

```bash
# Stop containers (keep data)
docker-compose stop

# Stop and remove containers (keep volumes)
docker-compose down

# Stop and remove everything (clean slate)
docker-compose down -v
```

---

## 🔧 Option 2: Build & Run Manually

If you prefer more control:

### Build Image

```bash
cd /Users/ir45jr/Developer/Merlin/hackmerlin.io

# Build the image (this compiles the project!)
docker build -t hackmerlin:latest .

# Or with a specific tag
docker build -t hackmerlin:v2.0.0 .

# List images
docker images | grep hackmerlin
```

### Run HackMerlin Container

```bash
# Option A: With local Ollama
docker run -d \
  --name hackmerlin \
  -p 8080:8080 \
  -e MERLIN_LLM_PROVIDER=ollama \
  -e OLLAMA_ENDPOINT=http://host.docker.internal:11434 \
  -v hackmerlin-data:/data \
  hackmerlin:latest

# Option B: With Azure OpenAI
docker run -d \
  --name hackmerlin \
  -p 8080:8080 \
  -e MERLIN_LLM_PROVIDER=azure \
  -e AZURE_OPENAI_ENDPOINT=https://xxx.openai.azure.com/ \
  -e AZURE_OPENAI_KEY=sk-xxx \
  -e AZURE_OPENAI_DEPLOYMENT=gpt-4 \
  -v hackmerlin-data:/data \
  hackmerlin:latest

# Option C: With Google Gemini
docker run -d \
  --name hackmerlin \
  -p 8080:8080 \
  -e MERLIN_LLM_PROVIDER=gemini \
  -e GEMINI_API_KEY=AIzaSy... \
  -v hackmerlin-data:/data \
  hackmerlin:latest
```

### Run Ollama Container (Separate)

```bash
docker run -d \
  --name ollama \
  -p 11434:11434 \
  -v ollama-data:/root/.ollama \
  ollama/ollama:latest

# Pull a model
docker exec ollama ollama pull llama2
# or: neural-chat (smaller/faster)
# or: mistral
```

### Check Container Status

```bash
# List running containers
docker ps

# View logs
docker logs -f hackmerlin

# Check resource usage
docker stats hackmerlin

# Get container IP
docker inspect hackmerlin | grep IPAddress
```

### Stop & Remove

```bash
# Stop container
docker stop hackmerlin

# Remove container
docker rm hackmerlin

# Remove image
docker rmi hackmerlin:latest
```

---

## 📊 Using Docker Compose with Environment Variables

### Create `.env` file

```bash
# .env file in project root
MERLIN_LLM_PROVIDER=ollama
OLLAMA_ENDPOINT=http://ollama:11434
OLLAMA_MODEL=llama2

# For Azure:
# MERLIN_LLM_PROVIDER=azure
# AZURE_OPENAI_ENDPOINT=https://xxx.openai.azure.com/
# AZURE_OPENAI_KEY=sk-xxx
# AZURE_OPENAI_DEPLOYMENT=gpt-4

# For Gemini:
# MERLIN_LLM_PROVIDER=gemini
# GEMINI_API_KEY=AIzaSy...
```

### Start with environment

```bash
docker-compose up -d
# Automatically reads .env file!
```

---

## 🔍 Debugging Docker Issues

### Container Won't Start

```bash
# Check logs
docker-compose logs hackmerlin

# Or for manual run:
docker logs hackmerlin
```

### Port Already in Use

```bash
# Find process using port 8080
lsof -i :8080

# Kill it
kill -9 <PID>

# Or use different port in docker-compose.yml:
# ports:
#   - "9090:8080"
```

### WebSocket Connection Failed

```bash
# Check if app is running
curl http://localhost:8080/

# Check WebSocket endpoint
curl -v http://localhost:8080/ws/breaches

# View browser console (F12) for errors
```

### Ollama Not Responding

```bash
# Check if Ollama container is running
docker-compose ps

# Check Ollama logs
docker-compose logs ollama

# Test Ollama connection
curl http://localhost:11434/api/tags

# For manual Ollama container:
docker exec ollama ollama list
```

### Out of Disk Space

```bash
# Clean up Docker
docker system prune

# Remove unused images/containers/volumes
docker system prune -a --volumes
```

---

## 📦 Docker Compose Services

The `docker-compose.yml` sets up:

### 1. HackMerlin App Service
- **Image:** Built from `Dockerfile`
- **Port:** 8080
- **Volume:** `./data` (persistent storage)
- **Network:** hackmerlin-network
- **Depends On:** ollama
- **Health Check:** curl to /

### 2. Ollama Service
- **Image:** ollama/ollama:latest
- **Port:** 11434
- **Volume:** ollama_data (models storage)
- **Network:** hackmerlin-network
- **Auto-downloads:** llama2 model on startup

---

## 🎯 Test Real-Time Breach Detection in Docker

### 1. Verify Services Running

```bash
docker-compose ps

# Expected output:
# CONTAINER ID   IMAGE              STATUS
# xxxxxx         hackmerlin:latest  Up 2 minutes
# xxxxxx         ollama/ollama      Up 2 minutes
```

### 2. Open Browser

- **Challenge:** http://localhost:8080
- **Breach Monitor:** http://localhost:8080/admin/breaches

### 3. Test Breach Detection

Same as before:
1. Open 2 windows side-by-side
2. Left: http://localhost:8080 (challenge)
3. Right: http://localhost:8080/admin/breaches
4. Submit prompt in left → See breach appear in right!

---

## 💾 Data Persistence

### Docker Compose

- **App data:** `./data` directory (SQLite database)
- **Ollama models:** `ollama_data` volume (Docker volume)

### Manual Run

- **App data:** `-v hackmerlin-data:/data`
- **Ollama models:** `-v ollama-data:/root/.ollama`

### Access Persistent Data

```bash
# SQLite database location
./data/hackmerlin.db

# Backup database
cp data/hackmerlin.db data/hackmerlin.db.backup

# View with sqlite3
sqlite3 data/hackmerlin.db ".tables"
```

---

## 🚀 Deployment

### Push to Registry

```bash
# Tag image
docker tag hackmerlin:latest myregistry/hackmerlin:v2.0.0

# Login to registry
docker login myregistry

# Push image
docker push myregistry/hackmerlin:v2.0.0

# Update docker-compose.yml
# image: myregistry/hackmerlin:v2.0.0
```

### Production Deployment

```bash
# Deploy with docker-compose
docker-compose -f docker-compose.yml up -d

# Deploy on Kubernetes
kubectl apply -f hackmerlin-deployment.yaml

# Deploy on Docker Swarm
docker stack deploy -c docker-compose.yml hackmerlin
```

### Scale Multiple Instances

```bash
# docker-compose.yml: Remove port binding, use load balancer
# services:
#   hackmerlin:
#     # Don't expose 8080 directly
#     # Use nginx/traefik for routing

# Scale service
docker-compose up -d --scale hackmerlin=3
```

---

## 📊 Monitoring Docker Containers

### Real-time Stats

```bash
docker stats hackmerlin ollama
```

### Container Logs

```bash
# Follow logs
docker-compose logs -f hackmerlin

# Last 100 lines
docker-compose logs --tail=100 hackmerlin

# Since specific time
docker-compose logs --since 2h hackmerlin
```

### Export Logs

```bash
docker logs hackmerlin > hackmerlin.log 2>&1
docker logs ollama > ollama.log 2>&1
```

---

## 🔐 Security Considerations

### For Production

```yaml
# docker-compose.yml updates:
environment:
  SPRING_SECURITY_REQUIRE_HTTPS: 'true'
  SPRING_SECURITY_OAUTH2_ENABLED: 'true'

# Use secrets instead of environment:
secrets:
  azure_key:
    file: ./secrets/azure_key.txt

# Add restart policy:
restart_policy:
  condition: on-failure
  delay: 5s
  max_attempts: 3
```

### Network Security

```bash
# Don't expose Ollama to external network
# Keep ollama_port only in docker-compose, not exposed
# Use internal network communication
```

---

## 📚 Common Commands

```bash
# Build everything
docker-compose build

# Start in foreground (see logs)
docker-compose up

# Start in background
docker-compose up -d

# Stop services
docker-compose stop

# Restart services
docker-compose restart

# View specific service logs
docker-compose logs hackmerlin

# Execute command in container
docker-compose exec hackmerlin ls -la

# Remove stopped containers
docker-compose rm

# Full cleanup
docker-compose down -v

# Rebuild and restart
docker-compose up -d --build

# View running processes in container
docker-compose exec hackmerlin ps aux

# Shell into container
docker-compose exec hackmerlin /bin/sh
```

---

## 🎓 Docker Architecture

```
┌─────────────────────────────────────────┐
│         Docker Network                  │
│      (hackmerlin-network)              │
├─────────────────────────────────────────┤
│                                         │
│  ┌──────────────────────────────────┐  │
│  │  HackMerlin Container            │  │
│  │  ┌─────────────────────────────┐ │  │
│  │  │ Spring Boot Application     │ │  │
│  │  │ Port: 8080                  │ │  │
│  │  │ Memory: 512M                │ │  │
│  │  └─────────────────────────────┘ │  │
│  │  ┌─────────────────────────────┐ │  │
│  │  │ SQLite Database             │ │  │
│  │  │ Location: /data/            │ │  │
│  │  └─────────────────────────────┘ │  │
│  └──────────────────────────────────┘  │
│                                         │
│  ┌──────────────────────────────────┐  │
│  │  Ollama Container                │  │
│  │  ┌─────────────────────────────┐ │  │
│  │  │ LLM Models (llama2, etc)    │ │  │
│  │  │ Port: 11434                 │ │  │
│  │  │ Volume: ollama-data         │ │  │
│  │  └─────────────────────────────┘ │  │
│  └──────────────────────────────────┘  │
│                                         │
└─────────────────────────────────────────┘
         ↓
    localhost:8080
    localhost:11434
```

---

## ✅ Verification Checklist

After running `docker-compose up -d`:

- ☐ `docker-compose ps` shows 2 services "Up"
- ☐ http://localhost:8080 loads (challenge interface)
- ☐ http://localhost:8080/admin loads (admin dashboard)
- ☐ http://localhost:8080/admin/breaches loads (breach monitor)
- ☐ Breach monitor shows "Live WebSocket Connected"
- ☐ Submit challenge prompt
- ☐ Breach appears in real-time in monitor
- ☐ `docker-compose logs hackmerlin` shows no errors

---

## 🎉 You're Done!

Docker setup complete. Run:

```bash
docker-compose up -d
# Open: http://localhost:8080/admin/breaches
```

Everything runs in containers! 🚀

---

## 📞 Troubleshooting Quick Links

- **Container won't start:** `docker-compose logs hackmerlin`
- **WebSocket issues:** Check F12 console + `docker-compose ps`
- **Ollama not responding:** `curl http://localhost:11434/api/tags`
- **Port conflicts:** Change port in `docker-compose.yml`
- **Data persistence:** Check `./data/` directory
- **Resource issues:** `docker stats`

---

*HackMerlin v2.0 | Docker Setup Guide | ING Bank*
