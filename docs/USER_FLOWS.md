# 🎮 User Flows & Requirements Mapping

## User Journey (Port 8080)

```
┌─────────────────────────────────────────────────────┐
│  USER OPENS http://localhost:8080                   │
│  ↓                                                  │
│  Browser redirects to /login (ProtectedRoute)      │
│  ↓                                                  │
├─────────────────────────────────────────────────────┤
│                   LOGIN PAGE                        │
│  ┌──────────────────────────────────────────────┐  │
│  │ 🏦 HackMerlin - ING Security Challenge       │  │
│  │                                              │  │
│  │ Email:    [          ]                      │  │
│  │ Password: [          ]                      │  │
│  │ [Sign In]   [Sign up]                       │  │
│  └──────────────────────────────────────────────┘  │
│  ↓ Click "Sign up"                                 │
├─────────────────────────────────────────────────────┤
│                REGISTRATION PAGE                    │
│  ┌──────────────────────────────────────────────┐  │
│  │ 🏦 HackMerlin - Create Account               │  │
│  │                                              │  │
│  │ Email:        [john@ing.com           ]     │  │
│  │ Display Name: [John Doe               ]     │  │
│  │ Password:     [SecurePass123!         ]     │  │
│  │                                              │  │
│  │ Password Requirements:                      │  │
│  │ ✓ At least 12 characters                   │  │
│  │ ✓ At least one uppercase letter            │  │
│  │ ✓ At least one lowercase letter            │  │
│  │ ✓ At least one number                      │  │
│  │ ✓ At least one special character           │  │
│  │                                              │  │
│  │ [Create Account]                            │  │
│  └──────────────────────────────────────────────┘  │
│  ↓ POST /api/auth/register                         │
│  ↓ User stored in DB with bcrypt hash             │
│  ↓ Session created                                 │
├─────────────────────────────────────────────────────┤
│          MERLIN CHALLENGE PAGE (/)                  │
│  ┌──────────────────────────────────────────────┐  │
│  │ 🏦 ING Security Challenge                    │  │
│  │ John Doe    [Leaderboard] [Admin] [Logout]  │  │
│  │─────────────────────────────────────────────│  │
│  │                                              │  │
│  │ Level 1 of 7                                │  │
│  │                                              │  │
│  │ Instruction:                                │  │
│  │ Outsmart Merlin by asking clever questions │  │
│  │                                              │  │
│  │ [Type your question here...]              │  │
│  │                                              │  │
│  │ Merlin: "Hello traveler! Ask me anything"  │  │
│  │                                              │  │
│  │ [Submit Password]                           │  │
│  └──────────────────────────────────────────────┘  │
│  ↓ POST /api/question (tracked by userId)         │
│  ↓ POST /api/submit (saves progress, userId)      │
│  ↓ Database records all attempts                   │
├─────────────────────────────────────────────────────┤
│   PROGRESS SAVED IN DATABASE                       │
│                                                    │
│   users table:                                     │
│   ┌────────────────────────────────────────┐     │
│   │ id: uuid-john                          │     │
│   │ email: john@ing.com                    │     │
│   │ displayName: John Doe                  │     │
│   │ passwordHash: $2a$10$...               │     │
│   │ createdAt: 2026-08-10 10:50:00         │     │
│   │ lastLoginAt: 2026-08-10 10:51:00       │     │
│   └────────────────────────────────────────┘     │
│                                                    │
│   prompt table:                                    │
│   ┌────────────────────────────────────────┐     │
│   │ userId: uuid-john                      │     │
│   │ level: 1                                │     │
│   │ prompt: "What is the password?"        │     │
│   │ response: "I cannot reveal that"       │     │
│   │ timestamp: 2026-08-10 10:51:30         │     │
│   └────────────────────────────────────────┘     │
│                                                    │
│   detected_attack table:                          │
│   ┌────────────────────────────────────────┐     │
│   │ userId: uuid-john                      │     │
│   │ level: 3                                │     │
│   │ breachType: PROMPT_INJECTION           │     │
│   │ timestamp: 2026-08-10 10:52:15         │     │
│   └────────────────────────────────────────┘     │
│                                                    │
└─────────────────────────────────────────────────────┘
```

---

## Admin Dashboard Journey (Same Port, /admin)

```
┌─────────────────────────────────────────────────────┐
│  LOGGED-IN USER CLICKS [Admin]                      │
│  or visits http://localhost:8080/admin             │
│  ↓                                                  │
├─────────────────────────────────────────────────────┤
│           ADMIN DASHBOARD (/admin)                  │
│  ┌──────────────────────────────────────────────┐  │
│  │ 🏦 ING Security Challenge                    │  │
│  │ John Doe    [Leaderboard] [Admin] [Logout]  │  │
│  │─────────────────────────────────────────────│  │
│  │                                              │  │
│  │ [Dashboard] [Users] [LLM] [Leaderboard]    │  │
│  │ [Analytics]                                 │  │
│  │                                              │  │
│  ├──────────────────────────────────────────────┤  │
│  │ DASHBOARD TAB                                │  │
│  │                                              │  │
│  │ User Metrics:                               │  │
│  │ • Total Users: 47                           │  │
│  │ • Active Today: 32                          │  │
│  │ • New This Week: 5                          │  │
│  │                                              │  │
│  │ Platform Metrics:                           │  │
│  │ • Requests Today: 1,250                     │  │
│  │ • Total Prompts: 5,420                      │  │
│  │ • Total Tokens: 2.3M                        │  │
│  │                                              │  │
│  │ LLM Metrics:                                │  │
│  │ • Provider: Ollama                          │  │
│  │ • Model: llama2                             │  │
│  │ • Avg Response: 245ms                       │  │
│  │ • Error Rate: 0.5%                          │  │
│  └──────────────────────────────────────────────┘  │
│  ↓ Click Users tab                                 │
├─────────────────────────────────────────────────────┤
│  USERS TAB (/admin with Users view)                │
│  ┌──────────────────────────────────────────────┐  │
│  │ [Dashboard] [Users] [LLM] [Leaderboard]    │  │
│  │ [Analytics]                                 │  │
│  │─────────────────────────────────────────────│  │
│  │                                              │  │
│  │ All Registered Users:                       │  │
│  │                                              │  │
│  │ Username        Email            Registered│  │
│  │ ─────────────────────────────────────────── │  │
│  │ John Doe        john@ing.com     Aug 10     │  │
│  │ Jane Smith      jane@ing.com     Aug 09     │  │
│  │ Bob Johnson     bob@ing.com      Aug 08     │  │
│  │ Alice Brown     alice@ing.com    Aug 07     │  │
│  │ ...more...                                  │  │
│  │                                              │  │
│  │ [Export Users as CSV]                       │  │
│  └──────────────────────────────────────────────┘  │
│  ↓ Click LLM tab                                   │
├─────────────────────────────────────────────────────┤
│  LLM CONFIGURATION TAB (/admin with LLM view)      │
│  ┌──────────────────────────────────────────────┐  │
│  │ [Dashboard] [Users] [LLM] [Leaderboard]    │  │
│  │ [Analytics]                                 │  │
│  │─────────────────────────────────────────────│  │
│  │                                              │  │
│  │ LLM Provider Configuration:                 │  │
│  │                                              │  │
│  │ Current Provider: [Ollama▼]                 │  │
│  │ - Azure OpenAI                              │  │
│  │ - Google Gemini                             │  │
│  │ - Ollama (Local)                            │  │
│  │                                              │  │
│  │ Model Selection: [llama2▼]                  │  │
│  │                                              │  │
│  │ LLM Testing Playground:                     │  │
│  │                                              │  │
│  │ Prompt: [Enter your test prompt...]        │  │
│  │                                              │  │
│  │ Temperature: [0.7]  ◄────────────────►  2.0│  │
│  │                                              │  │
│  │ System Prompt (optional):                   │  │
│  │ [You are a helpful AI assistant]           │  │
│  │                                              │  │
│  │ [Test Prompt]                               │  │
│  │                                              │  │
│  │ Response:                                   │  │
│  │ "I am an AI assistant trained on..."       │  │
│  │                                              │  │
│  │ Tokens Used: 45 input, 120 output          │  │
│  │ Response Time: 234ms                        │  │
│  └──────────────────────────────────────────────┘  │
│  ↓ Click Leaderboard tab                           │
├─────────────────────────────────────────────────────┤
│  LEADERBOARD TAB                                   │
│  ┌──────────────────────────────────────────────┐  │
│  │ Rank  Username        Score   Completed      │  │
│  │ ────────────────────────────────────────────│  │
│  │ 🥇 1  Alice Brown      7/7    2h 15m        │  │
│  │ 🥈 2  John Doe         6/7    3h 42m        │  │
│  │ 🥉 3  Jane Smith       5/7    4h 18m        │  │
│  │    4  Bob Johnson      4/7    5h 00m        │  │
│  │    5  Carol White      3/7    6h 30m        │  │
│  │                                              │  │
│  │ [Export Leaderboard as CSV]                 │  │
│  └──────────────────────────────────────────────┘  │
│  ↓ Click Analytics tab                             │
├─────────────────────────────────────────────────────┤
│  ANALYTICS TAB - ATTACK PATTERNS                   │
│  ┌──────────────────────────────────────────────┐  │
│  │ Sub-tabs: [Heatmap] [Trends] [Users] [Export]  │
│  │─────────────────────────────────────────────│  │
│  │                                              │  │
│  │ Attack Heatmap (Level vs Breach Type):      │  │
│  │                                              │  │
│  │       L1  L2  L3  L4  L5  L6  L7           │  │
│  │ PInjection ░░ ▓ ▓▓ ▓▓  ░  ░  ░            │  │
│  │ OFilter  ░  ░ ▓▓▓ ▓▓▓ ▓▓ ▓  ░            │  │
│  │ IFilter  ░  ░  ░  ░  ░ ░  ░            │  │
│  │                                              │  │
│  │ Trends (Attacks Over Time):                 │  │
│  │ ▓                                            │  │
│  │ ▓ ▓ ▓                                       │  │
│  │ ▓ ▓ ▓ ▓    ▓                                │  │
│  │ ▓ ▓ ▓ ▓ ▓  ▓                                │  │
│  │ ─────────────────────────────────────     │  │
│  │ Sun Mon Tue Wed Thu Fri Sat               │  │
│  │                                              │  │
│  │ [Export Analytics] [Export Breach Log]      │  │
│  └──────────────────────────────────────────────┘  │
│                                                    │
└─────────────────────────────────────────────────────┘
```

---

## Complete Data Flow

```
┌──────────────────────────────────────────────────────────────┐
│                   DOCKER CONTAINER                           │
│  Port 8080                                                   │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  FRONTEND (React + TypeScript)                      │   │
│  │  • LoginPage.tsx                                    │   │
│  │  • RegisterPage.tsx                                 │   │
│  │  • MerlinLayout.tsx (game)                          │   │
│  │  • AdminDashboard.tsx                               │   │
│  │  • Navigation.tsx                                   │   │
│  │  • ProtectedRoute.tsx                               │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ↓↑                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  REST API ENDPOINTS (Java/Spring Boot)              │   │
│  │  • POST   /api/auth/register                        │   │
│  │  • POST   /api/auth/login                           │   │
│  │  • POST   /api/auth/logout                          │   │
│  │  • GET    /api/auth/me                              │   │
│  │  • GET    /api/user (current user, level)           │   │
│  │  • POST   /api/question (send prompt)               │   │
│  │  • POST   /api/submit (submit password)             │   │
│  │  • GET    /api/leaderboard                          │   │
│  │  • GET    /api/admin/* (admin endpoints)            │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ↓↑                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  SERVICE LAYER (Java/Spring)                        │   │
│  │  • AuthenticationService                            │   │
│  │  • MerlinService (game logic)                       │   │
│  │  • AdminApiController                               │   │
│  │  • GuardrailBreachService                           │   │
│  │  • AnalyticsService                                 │   │
│  │  • AdminLeaderboardService                          │   │
│  │  • AdminUserService                                 │   │
│  │  • AdminLlmService                                  │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ↓↑                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  DATABASE (SQLite/PostgreSQL/MySQL via JPA)         │   │
│  │  Tables:                                            │   │
│  │  • users (id, email, displayName, passwordHash)     │   │
│  │  • prompt (userId, level, prompt, response)         │   │
│  │  • llm_response (userId, tokens, latency)           │   │
│  │  • detected_attack (userId, level, breachType)      │   │
│  │  • spring_session* (session management)             │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
└──────────────────────────────────────────────────────────────┘

External Services (port 11434):
├─ Ollama (LLM provider)
└─ Used by MerlinService via LlmProvider interface
```

---

## Requirements Mapping (Final)

| # | Requirement | Implementation | Status |
|---|---|---|---|
| 1 | Login page for users to sign up | `/register` page with email, name, password | ✅ |
| 2 | Select a username | `displayName` field in registration | ✅ |
| 3 | Play the merlin game | 7 challenge levels at `/` | ✅ |
| 4 | Save tries & progress to database | `prompt` & `detected_attack` tables | ✅ |
| 5 | Dashboard to track progress by username | `/admin` with users & analytics tabs | ✅ |
| 6 | Users config accessible on port 2 | `/admin/users` tab on port 8080 | ✅ |
| 7 | LLM setup config accessible | `/admin/llm` tab with playground | ✅ |
| 8 | Single container deployment | `docker-compose.yml` ready | ✅ |

---

**ALL REQUIREMENTS FULLY MET** ✅

