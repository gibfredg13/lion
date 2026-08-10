# Quick Start - Docker (Container-First)

## 🎯 Goal
Start HackMerlin in Docker with zero local dependencies in 2 commands.

## ⚡ Quick Start

### Step 1: Start Everything
```bash
docker-compose up -d
```

**Done!** This starts:
- ✅ Backend (Spring Boot)
- ✅ Frontend (React)
- ✅ Database (SQLite)
- ✅ Ollama (Local LLM)

### Step 2: Access Application
```
http://localhost:8080
```

## 📝 What You Can Do

### For Users
1. Visit `http://localhost:8080`
2. Click "Register"
3. Create account (email + password)
4. Play the game (7 levels)
5. Try to beat guardrails

### For Admins
1. Login with your account
2. Visit `http://localhost:8080/admin`
3. Configure LLM
4. Test prompts
5. View breaches
6. Monitor users

## 🛑 Stop Services

```bash
docker-compose down
```

## 📊 View Logs

```bash
# All services
docker-compose logs -f

# Only app
docker-compose logs -f hackmerlin-app

# Only Ollama
docker-compose logs -f ollama
```

## 🔌 Change LLM Provider

### Azure OpenAI
Create `.env` file:
```bash
MERLIN_LLM_PROVIDER=azure
AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
AZURE_OPENAI_KEY=your-key
AZURE_OPENAI_DEPLOYMENT=your-deployment
```

Then:
```bash
docker-compose up -d --build
```

### Google Gemini
Create `.env` file:
```bash
MERLIN_LLM_PROVIDER=gemini
GEMINI_API_KEY=your-key
```

Then:
```bash
docker-compose up -d --build
```

### Ollama (Default)
Already configured! Just:
```bash
docker-compose up -d
```

## 📁 File Structure

```
hackmerlin.io/
├── docker-compose.yml      # Everything you need
├── Dockerfile              # How to build the app
├── backend/                # Java source code
├── frontend/               # React source code
├── data/                   # SQLite database (created)
└── docs/                   # Full documentation
```

## 🔍 Common Tasks

### Check if containers are running
```bash
docker-compose ps
```

Output:
```
NAME                COMMAND                  STATUS
hackmerlin-app      "java -jar ..."         Up (healthy)
hackmerlin-ollama   "ollama serve"          Up
```

### Access database directly
```bash
docker exec -it hackmerlin-app sqlite3 /data/hackmerlin.db

# Then at prompt:
SELECT * FROM users;
.quit
```

### Rebuild after code changes
```bash
docker-compose build --no-cache
docker-compose up -d
```

### Clean everything
```bash
docker-compose down -v  # -v removes volumes (WARNING: deletes data)
```

## ✅ Verification

### Is app running?
```bash
curl http://localhost:8080
# Should get HTML response
```

### Is Ollama running?
```bash
curl http://localhost:11434/api/tags
# Should get list of available models
```

### Is database created?
```bash
docker exec -it hackmerlin-app ls -lh /data/
# Should see hackmerlin.db file
```

## 🐛 Troubleshooting

### Port 8080 already in use
```bash
# Find process using 8080
lsof -i :8080

# Either:
# 1. Kill the process: kill -9 <PID>
# 2. Use different port: docker-compose run -p 9000:8080
```

### Container exits immediately
```bash
docker-compose logs hackmerlin-app
# Read the error message
```

Common causes:
- Port already in use
- Out of disk space
- Bad environment variables

### Slow response times
Check if containers have enough resources:
```bash
docker stats
```

### App won't connect to Ollama
```bash
docker-compose logs ollama
# Check if model is downloaded

# Manually pull model:
docker exec hackmerlin-ollama ollama pull llama2
```

## 📚 More Information

- **Full Docker Guide**: `docs/DOCKER_DEPLOYMENT.md`
- **Configuration Options**: `docs/DOCKER_DEPLOYMENT.md#configuration`
- **Troubleshooting**: `docs/DOCKER_DEPLOYMENT.md#troubleshooting`
- **Production Deployment**: `docs/DOCKER_DEPLOYMENT.md#production-deployment`

## ⚙️ What docker-compose does

```yaml
hackmerlin-app:
  ✓ Builds Docker image
  ✓ Runs Spring Boot backend
  ✓ Runs React frontend
  ✓ Creates SQLite database
  ✓ Manages user sessions
  ✓ Connects to Ollama

ollama:
  ✓ Runs local LLM service
  ✓ Caches models
  ✓ Serves AI responses
  ✓ Communicates with backend

Network:
  ✓ Both containers on same network
  ✓ Port 8080 exposed for users
  ✓ Port 11434 exposed for debugging
  ✓ Isolated and secure
```

## 🎮 Next Steps

1. ✅ Start: `docker-compose up -d`
2. ✅ Access: `http://localhost:8080`
3. ✅ Register: Create your account
4. ✅ Play: Beat the guardrails!
5. ✅ Admin: Visit `/admin` to see stats

Enjoy! 🚀
