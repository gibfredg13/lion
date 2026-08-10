# HackMerlin - AI Security Awareness Training Platform

A browser-based game that teaches employees about AI security vulnerabilities through 7 progressive guardrail bypass levels. Built for ING Bank's internal security hackathon.

## 🎮 What is HackMerlin?

Players attempt to trick an AI model into revealing passwords by bypassing security guardrails. Each level increases difficulty:
- **Level 1**: No guardrail (trivial)
- **Level 7**: Advanced multi-layered protection (extreme)

See `docs/SYSTEM_OVERVIEW.txt` for complete overview.

## 📁 Project Structure

```
hackmerlin.io/
├── backend/              # Spring Boot + Java 21
├── frontend/             # React + TypeScript + Vite
├── docs/                 # Complete documentation (15+ guides)
├── Dockerfile            # Multi-stage Docker build
├── docker-compose.yml    # Orchestration
└── LAUNCH.sh             # Automated setup script
```

## 🚀 Quick Start

### Option 1: Docker (Recommended)
```bash
docker-compose up -d
open http://localhost:8080
```

### Option 2: Local Development (requires Java 21)
```bash
./gradlew bootRun       # Backend on :8080
cd frontend && npm run dev  # Frontend on :3000
```

## 📚 Documentation

All documentation has been organized in `docs/`:

| Document | Purpose | Read Time |
|----------|---------|-----------|
| [SYSTEM_OVERVIEW.txt](docs/SYSTEM_OVERVIEW.txt) | Complete system explanation | 15 min |
| [README_FOR_AI.txt](docs/README_FOR_AI.txt) | For AI systems helping with development | 25 min |
| [TECHNICAL_ARCHITECTURE.md](docs/TECHNICAL_ARCHITECTURE.md) | Architecture & component design | 45 min |
| [DOCUMENTATION_INDEX.md](docs/DOCUMENTATION_INDEX.md) | Navigation guide for all docs | 10 min |
| [DEPLOY_WITH_AUTH.md](docs/DEPLOY_WITH_AUTH.md) | Production deployment guide | 30 min |
| [BUILD_FIXES.md](docs/BUILD_FIXES.md) | Docker build troubleshooting | 5 min |

**Start with**: `docs/DOCUMENTATION_INDEX.md` for full navigation.

## 🔐 Features

- ✅ User authentication (email + password with bcrypt hashing)
- ✅ Multi-user progress tracking (all attempts recorded with email)
- ✅ Admin dashboard (real-time breach monitoring, user analytics)
- ✅ Multi-provider LLM support (Azure OpenAI, Google Gemini, Ollama)
- ✅ LLM testing playground (admin can test guardrails before release)
- ✅ Leaderboard (scores, times, levels)
- ✅ Data export (CSV, JSON)
- ✅ Single Docker container deployment
- ✅ SQLite persistence (or PostgreSQL for production)

## 🛠️ Configuration

Set environment variables or edit `backend/application.properties`:

```properties
# LLM Provider (azure, gemini, ollama)
LLM_PROVIDER=azure
LLM_API_KEY=your-api-key
LLM_BASE_URL=https://your-resource.openai.azure.com

# Database
SPRING_DATASOURCE_URL=jdbc:sqlite:./data/hackmerlin.db

# Session
SERVER_SERVLET_SESSION_TIMEOUT=30m
```

## 📋 Requirements Met

- ✅ User registration & login (email + password)
- ✅ Email & username tracking for all users
- ✅ Progress tracking per user per challenge
- ✅ Admin dashboard with real-time breach detection
- ✅ LLM provider configuration & testing
- ✅ Leaderboard with rankings & times
- ✅ Data export (user database, audit logs)
- ✅ Single container deployment
- ✅ Orange ING branding
- ✅ Security-focused audit capabilities

## 📦 Technology Stack

**Backend:**
- Java 21, Spring Boot 3.4.1, Spring Security, Spring Data JPA
- Azure OpenAI SDK, Gemini/Ollama REST APIs
- SQLite/PostgreSQL, JDBC Session Store

**Frontend:**
- React 19, TypeScript 5, Vite, Mantine UI
- React Query, React Router, WebSocket (real-time)

**Infrastructure:**
- Docker & Docker Compose
- Gradle 8.10 (multi-module build)

## 🔗 API Endpoints

See `docs/AI_SYSTEM_DOCUMENTATION.md` for complete API reference.

**Key Endpoints:**
- `POST /api/auth/register` - User registration
- `POST /api/auth/login` - User login
- `POST /api/question` - Submit prompt to LLM
- `POST /api/submit` - Submit password guess
- `GET /api/leaderboard` - Public leaderboard
- `GET /api/admin/*` - Admin endpoints (auth required)

## 🐛 Troubleshooting

- **Docker build fails**: See `docs/BUILD_FIXES.md`
- **Database issues**: Check `docs/DEPLOY_WITH_AUTH.md` → Troubleshooting
- **LLM not working**: Review `docs/TECHNICAL_ARCHITECTURE.md` → LLM Abstraction

## 📝 License

Internal use for ING Bank. Not for distribution.

## 📞 Support

Refer to documentation in `docs/` directory. Start with:
1. `docs/DOCUMENTATION_INDEX.md` (navigation)
2. `docs/SYSTEM_OVERVIEW.txt` (overview)
3. `docs/README_FOR_AI.txt` (technical detail)
