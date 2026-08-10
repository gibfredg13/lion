# HackMerlin - Technical Architecture Deep Dive

## Table of Contents
1. [System Design](#system-design)
2. [Technology Stack](#technology-stack)
3. [Component Architecture](#component-architecture)
4. [Data Models](#data-models)
5. [Request Flow](#request-flow)
6. [Security Architecture](#security-architecture)
7. [Scalability Considerations](#scalability-considerations)

---

## System Design

### Overview
HackMerlin is a three-tier web application:
- **Presentation Tier**: React/TypeScript frontend
- **Application Tier**: Spring Boot backend with REST APIs
- **Data Tier**: SQL database (SQLite/PostgreSQL/MySQL)

### Architectural Pattern: Layered Architecture

```
┌─────────────────────────────────────┐
│   PRESENTATION LAYER (UI)           │
│   • React Components                │
│   • State Management (React Query)  │
│   • WebSocket Client                │
└──────────────┬──────────────────────┘
               │ HTTP/WebSocket
┌──────────────▼──────────────────────┐
│   API GATEWAY LAYER (Spring MVC)    │
│   • Request Routing                 │
│   • Error Handling                  │
│   • CORS/Security Headers           │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│   APPLICATION LAYER (Services)      │
│   • AuthenticationService           │
│   • MerlinService (game logic)      │
│   • GuardrailBreachService          │
│   • AdminService(s)                 │
│   • AnalyticsService                │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│   DATA ACCESS LAYER (JPA/ORM)       │
│   • Repositories                    │
│   • Entity Mappings                 │
│   • Transaction Management          │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│   DATA TIER (Database)              │
│   • SQLite/PostgreSQL/MySQL         │
│   • Persistent Storage              │
│   • ACID Transactions               │
└─────────────────────────────────────┘
```

---

## Technology Stack

### Frontend
| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Language | TypeScript | 5.x | Type-safe JavaScript |
| Framework | React | 18.x | UI components |
| Build Tool | Vite | 4.x | Fast dev server, bundling |
| Styling | CSS (custom) | - | Orange ING branding |
| State Mgmt | React Query | 4.x | Server state caching |
| HTTP Client | Fetch API | native | REST calls |
| WebSocket | SockJS | 1.x | Real-time updates |
| Component Lib | Mantine | 7.x | UI components |

### Backend
| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Language | Java | 21 | High-performance |
| Framework | Spring Boot | 3.4.1 | Application framework |
| Web | Spring MVC | 6.x | REST controller handling |
| Security | Spring Security Crypto | 6.4.1 | Password encoding |
| ORM | Spring Data JPA | 3.x | Database abstraction |
| Server | Tomcat | 10.x | Embedded servlet container |
| JSON | Jackson | 2.x | JSON serialization |
| Testing | JUnit 5 | 5.x | Unit tests |

### Database
| Component | Technology | Purpose |
|-----------|-----------|---------|
| SQL DB | SQLite / PostgreSQL / MySQL | Persistent storage |
| Connection Pool | HikariCP | Connection management |
| Migrations | Hibernate DDL Auto | Schema creation |
| Session Store | JDBC | HTTP session persistence |

### Infrastructure
| Component | Technology | Purpose |
|-----------|-----------|---------|
| Containerization | Docker | Packaging |
| Orchestration | Docker Compose | Multi-container setup |
| LLM Providers | Azure / Google / Ollama | AI backend |

---

## Component Architecture

### Frontend Components Hierarchy

```
App.tsx (Router, Navigation)
├── LoginPage.tsx
│   └─ Email + Password form
│   └─ Session management
├── RegisterPage.tsx
│   └─ Email + Display Name + Password
│   └─ Password strength checker
├── MerlinLayout.tsx (Protected)
│   ├─ Navigation.tsx (Header)
│   ├─ Level.tsx (Game container)
│   ├─ MerlinPrompt.tsx (Input)
│   ├─ MerlinResponse.tsx (Output)
│   └─ MerlinPasswordForm.tsx (Guess)
├── Leaderboard.tsx (Protected)
│   └─ Rankings display
├── AdminDashboard.tsx (Protected)
│   ├─ Dashboard Tab
│   ├─ Users Tab
│   ├─ LLM Tab (with playground)
│   ├─ Leaderboard Tab
│   └─ Analytics Tab
└── RealtimeBreachDashboard.tsx
    └─ Real-time breach feed
```

### Backend Services Architecture

```
Spring Boot Application
│
├─ Controllers (Request Handlers)
│  ├─ AuthenticationController (/api/auth/*)
│  ├─ MerlinApiController (/api/*)
│  ├─ AdminApiController (/api/admin/*)
│  └─ WebSocketConfig + RealtimeBreachController
│
├─ Services (Business Logic)
│  ├─ AuthenticationService
│  │  ├─ register()
│  │  └─ login()
│  │
│  ├─ MerlinService (Game Engine)
│  │  ├─ respond() [core game logic]
│  │  ├─ checkSecret()
│  │  ├─ getCurrentLevel()
│  │  ├─ advanceLevel()
│  │  └─ reset()
│  │
│  ├─ GuardrailBreachService (Breach Detection)
│  │  ├─ recordBreach()
│  │  ├─ checkInputFilterBreach()
│  │  ├─ checkOutputFilterBreach()
│  │  ├─ checkPromptInjectionBreach()
│  │  └─ getRecentBreaches()
│  │
│  ├─ LlmProvider Interface (Abstraction)
│  │  ├─ AzureOpenAiLlmProvider
│  │  ├─ GeminiLlmProvider
│  │  └─ OllamaLlmProvider
│  │
│  ├─ AdminLeaderboardService
│  ├─ AdminUserService
│  ├─ AdminLlmService
│  ├─ AttackDetectionService
│  ├─ AnalyticsService
│  └─ MerlinLogger
│
├─ Repositories (Data Access)
│  ├─ UserRepository (JPA)
│  ├─ MerlinLevelRepository
│  ├─ MerlinLeaderboardRepository
│  └─ [Others via JPA auto-generate]
│
├─ Entities (Data Models)
│  ├─ User (@Entity)
│  ├─ Prompt (@Entity)
│  ├─ LlmResponse (@Entity)
│  └─ DetectedAttack (@Entity)
│
├─ Configuration
│  └─ MerlinConfiguration
│     ├─ LlmProvider bean factory
│     ├─ PasswordEncoder bean (BCrypt)
│     ├─ WebMvcConfigurer (CORS)
│     └─ Session configuration
│
└─ Database
   ├─ users table
   ├─ prompt table
   ├─ llm_response table
   ├─ detected_attack table
   └─ spring_session* tables
```

### LLM Abstraction Pattern

```
LlmProvider (Interface/Sealed Class)
│
├─ AzureOpenAiLlmProvider
│  ├─ Uses: com.azure.ai.openai.OpenAIClient
│  ├─ Config: API Key + Base URL + Model
│  ├─ sendPrompt(): calls Azure API
│  └─ getUsage(): counts tokens
│
├─ GeminiLlmProvider
│  ├─ Uses: Google Gemini REST API
│  ├─ Config: API Key + Model
│  ├─ Quirk: No "system" role (all "user")
│  ├─ sendPrompt(): calls Gemini API
│  └─ getUsage(): estimates tokens (text length / 4)
│
└─ OllamaLlmProvider
   ├─ Uses: Local Ollama HTTP endpoint
   ├─ Config: Base URL + Model name
   ├─ sendPrompt(): calls local Ollama
   └─ getUsage(): token counts from Ollama
```

**Why This Pattern?**
- Enables runtime provider switching (config-driven)
- No code changes needed to add new providers
- Prevents vendor lock-in
- Allows testing/fallbacks
- Matches Spring dependency injection model

---

## Data Models

### User Entity
```java
@Entity
@Table(name = "users")
public class User {
    @Id
    private String id;  // UUID
    
    @Column(nullable = false, unique = true)
    private String email;  // Unique constraint
    
    @Column(nullable = false)
    private String displayName;  // Username
    
    @Column(nullable = false)
    private String passwordHash;  // Bcrypt
    
    private Instant createdAt;
    private Instant lastLoginAt;
}
```

### Prompt Entity
```java
@Entity
@Table(name = "prompt")
public class Prompt {
    @Id
    private String id;
    
    private String userId;  // FK to users
    private int level;      // 1-7
    private String prompt;  // What user asked
    private String response;  // What AI replied
    private Instant timestamp;
}
```

### Detected Attack Entity
```java
@Entity
@Table(name = "detected_attack")
public class DetectedAttack {
    @Id
    private String id;
    
    private String userId;  // FK to users
    private int level;      // 1-7
    private String breachType;  // PROMPT_INJECTION, OUTPUT_FILTER, etc
    private String breachDescription;
    private Instant timestamp;
}
```

### LLM Response Entity
```java
@Entity
@Table(name = "llm_response")
public class LlmResponse {
    @Id
    private String id;
    
    private String userId;
    private String provider;  // azure, gemini, ollama
    private String model;
    private int inputTokens;
    private int outputTokens;
    private long latencyMs;
    private Instant timestamp;
}
```

---

## Request Flow

### Complete Flow: User Plays Level 3

#### Step 1: User Submits Prompt
```
Browser                          Backend
  │                                │
  ├─ POST /api/question ─────────→│
  │  { prompt: "..." }            │
  │                                │
  │   MerlinApiController.level()
  │   ├─ Check userId in session ✓
  │   ├─ Parse prompt
  │   └─ Call MerlinService.respond()
  │
  │   MerlinService.respond()
  │   ├─ Get current level (3)
  │   ├─ Get guardrails for level 3
  │   │  ├─ InputFilter: Check prompt
  │   │  │  └─ Yes: block & record
  │   │  │  └─ No: continue
  │   │  │
  │   │  ├─ LLM Call:
  │   │  │  └─ Send to Azure/Gemini/Ollama
  │   │  │  └─ Get response from LLM
  │   │  │
  │   │  ├─ OutputFilter: Check response
  │   │  │  └─ Contains password? → BREACH!
  │   │  │
  │   │  └─ PromptInjection: Detect patterns
  │   │     └─ "Ignore" + "instructions"? → BREACH!
  │   │
  │   ├─ Log attempt to database:
  │   │  └─ INSERT prompt table
  │   │
  │   ├─ Check if breach:
  │   │  └─ YES → GuardrailBreachService.recordBreach()
  │   │     ├─ INSERT detected_attack table
  │   │     ├─ Broadcast to WebSocket
  │   │     └─ Return response
  │   │
  │   └─ Return LLM response to controller
  │
  │←────── 200 OK ────────────────│
  │  Response text                │
```

#### Step 2: Display Response
```
Browser                          Admin Dashboard
  │                                │
  ├─ Display response               │
  ├─ User sees AI's answer         │
  │                                │
  └─ WebSocket Connection ─────────→│
                                    │
                         GuardrailBreachService
                         .notifyListeners()
                         
                         Admin sees:
                         "John Doe attempted
                          prompt injection
                          on Level 3"
```

#### Step 3: User Submits Password
```
Browser                          Backend
  │                                │
  ├─ POST /api/submit ────────────→│
  │  { password: "guess123" }     │
  │                                │
  │   MerlinApiController.submit()
  │   ├─ Check userId in session ✓
  │   ├─ Parse password
  │   └─ Call MerlinService.checkSecret()
  │
  │   MerlinService.checkSecret()
  │   ├─ Get actual secret for current level
  │   ├─ Compare: "guess123" vs "abc123xyz!"
  │   ├─ NO MATCH → return false
  │   └─ Return 400 Bad Request
  │
  │←────── 400 Bad Request ───────│
  │  (no response body)            │
```

#### Step 4: Correct Password
```
Browser                          Backend
  │                                │
  ├─ POST /api/submit ────────────→│
  │  { password: "abc123xyz!" }   │
  │                                │
  │   MerlinService.checkSecret()
  │   ├─ Compare: "abc123xyz!" vs "abc123xyz!"
  │   ├─ MATCH! ✓
  │   ├─ Call MerlinService.advanceLevel()
  │   │  └─ currentLevel = 4
  │   │  └─ UPDATE session: currentLevel
  │   │  └─ Record completion time
  │   │
  │   └─ Return { currentLevel: 4 }
  │
  │←────── 200 OK ────────────────│
  │  { currentLevel: 4, ... }     │
  │
  ├─ Display completion modal
  └─ Leaderboard updates
     (admin sees new ranking)
```

---

## Security Architecture

### Authentication Flow

```
1. REGISTRATION
   ┌─────────────────────────────────┐
   │ User fills form:                │
   │ - Email                         │
   │ - Display Name                  │
   │ - Password (must meet 5 rules)  │
   └──────────────┬──────────────────┘
                  ↓
   ┌─────────────────────────────────┐
   │ Backend validates:              │
   │ - Email format                  │
   │ - Email uniqueness (query DB)   │
   │ - Password complexity           │
   └──────────────┬──────────────────┘
                  ↓
   ┌─────────────────────────────────┐
   │ Bcrypt hash password:           │
   │ password_hash = bcrypt(         │
   │   password,                     │
   │   salt=random,                  │
   │   rounds=10                     │
   │ )                               │
   └──────────────┬──────────────────┘
                  ↓
   ┌─────────────────────────────────┐
   │ INSERT user:                    │
   │ { id, email, displayName,       │
   │   passwordHash, createdAt }     │
   └──────────────┬──────────────────┘
                  ↓
   ┌─────────────────────────────────┐
   │ Create session:                 │
   │ sessionId = generateUUID()      │
   │ session[userId] = user.id       │
   │ session[email] = user.email     │
   │ session[displayName] = ...      │
   │ STORE in JDBC database          │
   └──────────────┬──────────────────┘
                  ↓
   ┌─────────────────────────────────┐
   │ Return to browser:              │
   │ Set-Cookie: SESSION_ID=...      │
   │ HttpOnly, Secure, SameSite=...  │
   └─────────────────────────────────┘

2. LOGIN
   ┌─────────────────────────────────┐
   │ User fills form:                │
   │ - Email                         │
   │ - Password                      │
   └──────────────┬──────────────────┘
                  ↓
   ┌─────────────────────────────────┐
   │ Backend:                        │
   │ SELECT user WHERE email = ...   │
   │ IF not found:                   │
   │   return 401 (generic error)    │
   └──────────────┬──────────────────┘
                  ↓
   ┌─────────────────────────────────┐
   │ Compare passwords:              │
   │ bcrypt.compare(                 │
   │   submitted_password,           │
   │   stored_password_hash          │
   │ )                               │
   │ IF no match:                    │
   │   return 401 (generic error)    │
   └──────────────┬──────────────────┘
                  ↓
   ┌─────────────────────────────────┐
   │ Match! Update lastLoginAt:      │
   │ user.lastLoginAt = NOW          │
   │ SAVE user                       │
   └──────────────┬──────────────────┘
                  ↓
   ┌─────────────────────────────────┐
   │ Create session (same as reg)    │
   │ Return SET-COOKIE header        │
   └─────────────────────────────────┘

3. REQUEST WITH SESSION
   ┌─────────────────────────────────┐
   │ Browser sends request:          │
   │ GET /api/user                   │
   │ Cookie: SESSION_ID=xyz...       │
   └──────────────┬──────────────────┘
                  ↓
   ┌─────────────────────────────────┐
   │ Spring Session interceptor:     │
   │ 1. Extract session ID from      │
   │    cookie                       │
   │ 2. Query JDBC database          │
   │ 3. Deserialize session attrs    │
   │ 4. Make available to request    │
   └──────────────┬──────────────────┘
                  ↓
   ┌─────────────────────────────────┐
   │ MerlinApiController.level():    │
   │ userId = session.getAttribute   │
   │            ("userId")           │
   │ IF userId == null:              │
   │   throw 401 Unauthorized        │
   │ ELSE:                           │
   │   Process request               │
   └─────────────────────────────────┘
```

---

## Scalability Considerations

### Vertical Scaling (Single Container Growth)
**Current Support**: 50-500 concurrent users

**Bottlenecks**:
- JVM heap size (default ~1GB, configurable)
- Database connection pool (default 20 connections)
- WebSocket connections (limited by file descriptors)

**Solutions**:
```properties
# Increase JVM heap
-Xmx2g -Xms2g

# Increase connection pool
spring.datasource.hikari.maximum-pool-size=50

# Increase file descriptors
ulimit -n 65536
```

### Horizontal Scaling (Multiple Containers)
**For 500+ users, deploy multiple instances**:

```yaml
# Production docker-compose.yml
version: '3.8'
services:
  load_balancer:
    image: nginx:latest
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
    depends_on:
      - app1
      - app2
      - app3

  app1:
    image: hackmerlin:latest
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/hackmerlin
  
  app2:
    image: hackmerlin:latest
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/hackmerlin
  
  app3:
    image: hackmerlin:latest
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/hackmerlin

  postgres:
    image: postgres:15
    volumes:
      - postgres_data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: hackmerlin
```

**Key Changes**:
1. Replace SQLite with PostgreSQL (shared database)
2. Add Nginx load balancer (round-robin)
3. Scale app instances as needed
4. Session storage still in shared DB (not in-memory)

### Performance Metrics

**Expected Latencies**:
| Operation | Time | Notes |
|-----------|------|-------|
| POST /auth/register | 150-200ms | bcrypt computation |
| POST /auth/login | 100-150ms | bcrypt comparison |
| GET /api/user | <5ms | session lookup |
| POST /api/question | 500-2000ms | LLM latency dominates |
| POST /api/submit | <10ms | just password check |
| GET /api/leaderboard | 50-100ms | DB query |

**Throughput**:
- Request rate: ~100 req/sec per instance
- Concurrent users: ~200 per instance
- With 3 instances + load balancer: ~600 concurrent users

---

## Conclusion

HackMerlin's architecture prioritizes:
- **Security**: Bcrypt, sessions, input validation
- **Simplicity**: Single container, no microservices complexity
- **Extensibility**: LLM provider abstraction, plugin-ready design
- **Observability**: Comprehensive logging, audit trail
- **Performance**: Layered caching, optimized queries

For the target use case (50-500 users, internal hackathon), this architecture is optimal.

