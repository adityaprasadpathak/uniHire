# UniHire Software Architecture Document (`ARCHITECTURE.md`)

This document provides a comprehensive technical architecture specification for the **UniHire** recruitment and talent acquisition platform. Every module, package, service, entity, API endpoint, background worker, caching mechanism, and communication flow documented herein directly corresponds to the source code implementation across the Spring Boot backend (`uniHire`) and React/Vite frontend (`uniHire-ui`).

---

## 1. System Context & High-Level Architecture

UniHire is structured as an enterprise AI-enhanced hiring platform. It supports three distinct tenant roles: **Candidate**, **Employer**, and **System Administrator**, orchestrating complex workflows such as asynchronous AI resume parsing, ATS scoring, vector-based semantic candidate-job matching, fuzzy full-text search, and real-time chat.

### 1.1 High-Level Architecture Diagram

```mermaid
graph TD
    subgraph Clients["Clients & Presentation Tier"]
        UI["React 19 / Vite Web SPA<br/>(Tailwind CSS, TanStack Query, Zustand)"]
        APK["Mobile App<br/>(Expo / React Native)"]
    end

    subgraph EdgeBoundary["Gateway, Security & Filter Pipeline"]
        RLF["RateLimitFilter<br/>(Redis Token-Bucket, Fail-Open)"]
        JWT["JwtFilter<br/>(HMAC-SHA256 Token Validator)"]
        WS_SEC["JwtChannelInterceptor<br/>(STOMP Frame Authentication)"]
        CORS["CORS & Spring Security Chain<br/>(Stateless Session Policy)"]
    end

    subgraph AppServer["Spring Boot 4.1.1 Enterprise Core (Java 27)"]
        subgraph ControllersLayer["REST & WebSocket Controllers"]
            AuthCtrl["AuthController"]
            AdminCtrl["AdminController"]
            CandCtrl["CandidateController & CandidateAppController"]
            EmpCtrl["EmployerController & EmployerAppController"]
            JobCtrl["JobController & PublicJobController"]
            ResumeCtrl["ResumeBuilderController"]
            RecCtrl["RecommendationController"]
            ChatCtrl["ChatController (STOMP /ws)"]
        end

        subgraph CoreServices["Domain Business Logic Services"]
            AuthSvc["AuthService & RefreshTokenService"]
            CandSvc["CandidateService"]
            EmpSvc["EmployerService & PublicEmployerService"]
            JobSvc["JobService & PublicJobService"]
            AppSvc["ApplicationService"]
            ChatSvc["ChatService"]
            AnalyticsSvc["AnalyticsService"]
        end

        subgraph AISubsystem["AI & Intelligence Subsystem"]
            RPS["ResumeParsingService<br/>(Apache Tika + Spring AI)"]
            RBS["ResumeBuilderService & ResumeGenService"]
            ATS["AtsScoringEngine"]
            AIF["AiFeatureService<br/>(Cover Letters & Match Scoring)"]
            SEM["SemanticSearchService<br/>(pgvector + Gemini Embeddings)"]
            JVS["JobViewSyncService"]
        end

        subgraph WorkerEngine["Distributed Background Execution"]
            JobRunr["JobRunr 7.3.2 Server<br/>(PostgreSQL-backed Background Queue)"]
            JobRunrUI["JobRunr Dashboard (:8000)"]
        end
    end

    subgraph DataTier["Data Persistence & Infrastructure Tier"]
        PG[("PostgreSQL 16+<br/>(ACID Relational Store)")]
        PGV[("pgvector Extension<br/>(3072-dim Cosine Embeddings)")]
        OS[("OpenSearch 2.19.0<br/>(Fuzzy Full-Text & Lexical Index)")]
        RD[("Redis 7.0+<br/>(Cache & Rate Limiting)")]
        OBJ[("S3 Object Storage<br/>(MinIO / Cloudflare R2)")]
        GEMINI["Google Gemini Cloud API<br/>(gemini-3.5-flash / text-embedding-004)"]
    end

    UI -->|HTTPS REST| RLF
    UI -->|WSS SockJS| WS_SEC
    APK -->|HTTPS REST| RLF

    RLF --> JWT --> CORS --> ControllersLayer
    WS_SEC --> ChatCtrl

    ControllersLayer --> CoreServices
    ControllersLayer --> AISubsystem

    CoreServices --> PG
    CoreServices --> RD
    CoreServices --> OS

    AISubsystem --> JobRunr
    JobRunr --> RPS
    JobRunr --> SEM

    RPS --> OBJ
    RPS --> GEMINI
    RBS --> GEMINI
    ATS --> GEMINI
    AIF --> GEMINI
    SEM --> GEMINI
    SEM --> PGV
    JobSvc --> OS
    JobSvc --> PGV
    JVS --> OS
```

---

## 2. Component Architecture & Package Decomposition

### 2.1 Backend Package Layout (`com.unihire`)

| Package | Key Classes | Core Responsibilities |
| :--- | :--- | :--- |
| **`config`** | `SecurityConfig`, `JwtFilter`, `RateLimitFilter`, `OpenSearchConfig`, `RedisCacheConfig`, `JobRunrConfig`, `WebSocketConfig`, `AdminDataInitializer`, `DatabaseCleanupConfig` | Configures Spring Security, stateless JWT auth, CORS, Redis caching fallback, JobRunr storage, OpenSearch HTTP client with SSL bypass, and WebSocket STOMP brokers. |
| **`controllers`** | `AuthController`, `CandidateController`, `EmployerController`, `JobController`, `PublicJobController`, `ResumeBuilderController`, `RecommendationController`, `ChatController`, `AdminController`, `AiFeatureController` | Exposes REST APIs, manages HTTP status codes (200, 201, 202, 204), extracts `@AuthenticationPrincipal User`, validates payloads via `@Valid`, and routes STOMP frames. |
| **`services`** | `AuthService`, `CandidateService`, `EmployerService`, `JobService`, `PublicJobService`, `ResumeBuilderService`, `ResumeParsingService`, `RecommendationService`, `ChatService`, `AiFeatureService`, `SemanticSearchService` | Encapsulates transactional business logic, cache annotations (`@Cacheable`, `@CacheEvict`), domain validations, and coordination between search/AI providers. |
| **`services.ai`** | `ResumeGenerationProvider`, `GeminiResumeGenerationProvider`, `MockResumeGenerationProvider` | Provides pluggable AI strategies for resume bullet point rewriting and executive summary tailoring using Gemini ChatClient. |
| **`services.extractor`**| `DocumentTextExtractor` | Ingests multipart files (PDF, DOCX) and parses raw textual streams using Apache Tika. |
| **`opensearch`** | `JobDocument`, `JobSearchRepository`, `CandidateDocument`, `CandidateSearchRepository` | Maps domain models to OpenSearch indices, executing multi-match fuzzy queries and aggregations. |
| **`models`** | `User`, `CandidateProfile`, `EmployerProfile`, `Job`, `JobApplication`, `Experience`, `Education`, `Certification`, `ResumeVersion`, `ResumeParsingJob`, `ChatMessage`, `Recommendation` | JPA entity models decorated with Hibernate annotations, lifecycle listeners, and enumerated states (`Role`, `JobStatus`, `ApplicationStatus`). |
| **`repositories`** | `UserRepository`, `CandidateProfileRepository`, `JobRepository`, `JobApplicationRepository`, `ResumeVersionRepository`, `ChatMessageRepository`, `JobSpecification` | Spring Data JPA interfaces providing declarative CRUD, pagination, and dynamic query filtering via JPA Criteria Specifications. |
| **`dto`** | `AuthResponse`, `LoginRequest`, `JobRequestDto`, `JobResponseDto`, `ResumeVersionDataDto`, `TargetCustomizationRequest`, `ParsedResumeResponse`, `AtsScoreDto` | Strongly-typed Data Transfer Objects preventing entity over-exposure and validating inputs. |
| **`utils`** | `DateUtils` | Enforces consistent `Month-Year` format (`Sept-2026 - Present`) across all AI-generated dates, parsing ISO, numeric, and text formats. |
| **`exceptions`** | `GlobalExceptionHandler`, `BadRequestException`, `ResourceNotFoundException`, `UnauthorizedOperationException` | Centralized `@RestControllerAdvice` converting application exceptions into standard RFC 7807 JSON error responses. |

### 2.2 Component Architecture Diagram

The component diagram below visualizes the internal structural relationships, package dependencies, and data tier interactions of the UniHire system:

```mermaid
graph TD
    subgraph UI_Components["Frontend Components Layer (React 19 / TypeScript)"]
        UI_Pages["Pages<br/>(JobBoard, ResumeBuilder, CandidateApps, Dashboard)"]
        UI_State["Zustand Store & React Query<br/>(Auth, Session, Cache)"]
        UI_API["API Clients<br/>(apiClient.ts, stompClient.ts, resumeBuilderApi.ts)"]
        UI_Pages --> UI_State
        UI_State --> UI_API
    end

    subgraph Security_Component["Security Filter Component"]
        RLF_Comp["RateLimitFilter<br/>(Redis Token Bucket)"]
        JWT_Comp["JwtFilter<br/>(JJWT Validator)"]
        SEC_CTX["SecurityContextHolder<br/>(User Principal + Roles)"]
        RLF_Comp --> JWT_Comp
        JWT_Comp --> SEC_CTX
    end

    subgraph Controller_Components["REST & WebSocket Controllers Layer"]
        C_Auth["AuthController"]
        C_Cand["CandidateController & CandidateAppController"]
        C_Emp["EmployerController & EmployerAppController"]
        C_Job["JobController & PublicJobController"]
        C_Resume["ResumeBuilderController"]
        C_Rec["RecommendationController"]
        C_Chat["ChatController (STOMP)"]
    end

    subgraph Service_Components["Business Logic & AI Components Layer"]
        S_Auth["AuthService & RefreshTokenService"]
        S_Cand["CandidateService"]
        S_Job["JobService & PublicJobService"]
        S_Resume["ResumeBuilderService & ResumeGenService"]
        S_Parse["ResumeParsingService (Apache Tika)"]
        S_Sem["SemanticSearchService"]
        S_Rec["RecommendationService"]
        S_Chat["ChatService"]
        S_Ats["AtsScoringEngine"]
    end

    subgraph Persistence_Components["Repositories & Data Access Layer"]
        R_User["UserRepository"]
        R_Cand["CandidateProfileRepository"]
        R_Job["JobRepository & JobSpecification"]
        R_App["JobApplicationRepository"]
        R_Resume["ResumeVersionRepository & ParsingJobRepository"]
        R_Chat["ChatMessageRepository"]
        R_OS["JobSearchRepository & CandidateSearchRepository"]
    end

    subgraph Infra_Components["Infrastructure Drivers & External APIs"]
        D_PG[("PostgreSQL 16 Engine")]
        D_PGV[("pgvector Extension")]
        D_OS[("OpenSearch 2.19 HTTP Client")]
        D_RD[("Redis Lettuce Driver")]
        D_S3[("MinIO / Cloudflare R2 S3 SDK")]
        D_JR["JobRunr Background Engine"]
        D_AI["Spring AI ChatClient (Google Gemini)"]
    end

    UI_API -->|REST HTTP| RLF_Comp
    UI_API -->|STOMP WS| C_Chat
    SEC_CTX --> Controller_Components

    C_Auth --> S_Auth
    C_Cand --> S_Cand
    C_Emp --> S_Job
    C_Job --> S_Job
    C_Resume --> S_Resume
    C_Resume --> S_Parse
    C_Rec --> S_Rec
    C_Chat --> S_Chat

    S_Auth --> R_User
    S_Cand --> R_Cand
    S_Cand --> S_Sem
    S_Job --> R_Job
    S_Job --> R_OS
    S_Job --> S_Sem
    S_Resume --> R_Resume
    S_Resume --> S_Ats
    S_Resume --> D_AI
    S_Parse --> D_S3
    S_Parse --> D_AI
    S_Parse --> D_JR
    S_Sem --> D_PGV
    S_Sem --> D_AI
    S_Rec --> D_PGV
    S_Chat --> R_Chat

    R_User --> D_PG
    R_Cand --> D_PG
    R_Job --> D_PG
    R_App --> D_PG
    R_Resume --> D_PG
    R_Chat --> D_PG
    R_OS --> D_OS

    S_Cand --> D_RD
    S_Rec --> D_RD
    RLF_Comp --> D_RD
```

### 2.3 Frontend Architecture (`uniHire-ui`)

The frontend application (`C:\reactProject\uniHire-frontEnd\apps\uniHire-ui`) is structured as a client-side single-page application:
- **State & Network Layer**:
  - `src/services/apiClient.ts`: Axios instance configured with base URL `/api`, authorization interceptors attaching JWT Bearer tokens from `localStorage`, and response interceptors handling 401 unauthenticated redirects.
  - `src/services/stompClient.ts`: Manages STOMP over SockJS client lifecycle, subscription mappings, and message frame dispatch.
  - `src/store/`: Zustand stores for user session, active application contexts, and transient state.
- **API Clients**:
  - `src/api/resumeBuilderApi.ts`: Client for resume compilation, generation polling, file upload, and document downloading.
  - `src/api/recommendationApi.ts`: Queries candidate and job recommendation feeds.
  - `src/api/admin.ts`: Administrative endpoints for user management and lock status toggling.
- **Resume Preview Rendering Engine**:
  - `src/components/candidate/ResumePreviewRenderer.tsx`: Pure HTML5 client-side rendering engine implementing 8 specialized ATS templates (`ATS_MODERN`, `CORPORATE`, `MINIMAL`, `EXECUTIVE`, `CREATIVE`, `ACADEMIC`, `TECH_STARTUP`, `MODERN_SPLIT`).

---

## 3. Microservices & Subsystem Communication

Although packaged as a modular monolith in Spring Boot for atomic transactions and simplified deployment, UniHire is engineered as distinct decoupled subsystems communicating over well-defined boundaries:

```mermaid
graph LR
    subgraph REST_Gateway["REST / HTTP Transport"]
        FE["Frontend (React UI)"] -->|JSON API Requests| HTTP_SRV["HTTP Dispatcher"]
    end

    subgraph RealTime["Real-Time Transport"]
        FE -->|STOMP over SockJS| WS_EP["/ws Endpoint"]
        WS_EP --> INTR["JwtChannelInterceptor"]
        INTR --> BROKER["In-Memory Message Broker"]
        BROKER -->|/topic/chat/{appId}| FE
    end

    subgraph AsyncBus["Asynchronous Background Queue (JobRunr)"]
        CORE["Core Business Services"] -->|Enqueue Job Lambda| JR_QUEUE[("PostgreSQL JobRunr Queue")]
        JR_QUEUE -->|Poll & Lease Lock| JR_WORKER["JobRunr Background Worker"]
        JR_WORKER --> AI_PARSE["ResumeParsingService"]
        JR_WORKER --> AI_REC["RecommendationService"]
    end

    subgraph ExtIntegrations["External Third-Party APIs"]
        JR_WORKER -->|HTTPS REST| S3_API["Object Storage (MinIO / R2)"]
        JR_WORKER -->|HTTPS REST| GEMINI_API["Google Gemini API (Chat & Embedding)"]
        CORE -->|HTTPS REST / HTTP5| OS_API["OpenSearch REST Engine (:9200)"]
    end
```

### 3.1 Inter-Subsystem Communication Rules
1. **Synchronous HTTP**: Used for user authentication, CRUD operations on jobs/profiles, and fetching dashboard summaries where immediate data consistency is mandatory.
2. **Asynchronous Polling Pattern**: Used for long-running AI operations (resume parsing, ATS scoring, resume customization). The client receives a `202 Accepted` status with a Job ID, and background workers process the task via JobRunr while the client polls `/api/resume-builder/versions/{id}` or checks parsing job status.
3. **STOMP Pub-Sub**: Used exclusively for candidate-employer chat messaging to deliver updates without client-side polling.
4. **Cache Invalidation Coupling**: State mutations in `CandidateService` trigger explicit cache evictions across `@CacheEvict(value = "candidateProfiles")`, `@CacheEvict(value = "dashboardMetrics")`, and `@CacheEvict(value = "recommendations")`. In background worker threads (JobRunr), caches are evicted programmatically via `CacheManager` to bypass Spring AOP proxy limitations.

---

## 4. Database Architecture & Entity Relationships

The relational persistence tier is hosted on PostgreSQL 16+ and extended with `pgvector` for vector storage.

### 4.1 Database Entity-Relationship (ER) Diagram

```mermaid
erDiagram
    USERS ||--o| CANDIDATE_PROFILES : "has profile"
    USERS ||--o| EMPLOYER_PROFILES : "has profile"
    USERS ||--o{ REFRESH_TOKENS : "owns"

    CANDIDATE_PROFILES ||--o{ EXPERIENCES : "has many"
    CANDIDATE_PROFILES ||--o{ EDUCATIONS : "has many"
    CANDIDATE_PROFILES ||--o{ CERTIFICATIONS : "has many"
    CANDIDATE_PROFILES ||--o{ CANDIDATE_DOCUMENTS : "uploads"
    CANDIDATE_PROFILES ||--o{ RESUME_PARSING_JOBS : "tracks"
    CANDIDATE_PROFILES ||--o{ RESUME_VERSIONS : "owns versions"
    CANDIDATE_PROFILES ||--o{ JOB_APPLICATIONS : "applies with"
    CANDIDATE_PROFILES ||--o{ RECOMMENDATIONS : "receives"

    EMPLOYER_PROFILES ||--o{ JOBS : "publishes"

    JOBS ||--o{ JOB_APPLICATIONS : "receives"
    JOBS ||--o{ RECOMMENDATIONS : "suggested as"
    JOBS ||--o{ JOB_VIEWS : "tracked in"

    JOB_APPLICATIONS ||--o{ CHAT_MESSAGES : "contains discussion"

    USERS {
        bigint id PK
        varchar email UK
        varchar password
        varchar role
        boolean is_locked
        timestamp created_at
    }

    REFRESH_TOKENS {
        bigint id PK
        bigint user_id FK
        varchar token UK
        timestamp expiry_date
    }

    CANDIDATE_PROFILES {
        bigint id PK
        bigint user_id FK
        varchar first_name
        varchar last_name
        varchar phone
        text summary
        varchar portfolio_url
        varchar parsing_status
        text parsing_failure_reason
        int profile_completion_score
    }

    EMPLOYER_PROFILES {
        bigint id PK
        bigint user_id FK
        varchar company_name
        varchar company_website
        varchar company_address
        varchar contact_phone
        varchar verification_status
    }

    JOBS {
        bigint id PK
        bigint employer_id FK
        varchar title
        text description
        varchar department
        varchar location
        varchar employment_type
        varchar status
        int min_experience_years
        int max_experience_years
        vector embedding_3072
        timestamp created_at
    }

    JOB_APPLICATIONS {
        bigint id PK
        bigint job_id FK
        bigint candidate_profile_id FK
        varchar status
        int ai_match_score
        text ai_feedback
        timestamp applied_at
    }

    EXPERIENCES {
        bigint id PK
        bigint candidate_profile_id FK
        varchar job_title
        varchar company
        varchar start_date
        varchar end_date
        text description
    }

    EDUCATIONS {
        bigint id PK
        bigint candidate_profile_id FK
        varchar degree
        varchar institution
        varchar start_date
        varchar end_date
        text description
    }

    RESUME_VERSIONS {
        bigint id PK
        bigint candidate_profile_id FK
        varchar version_name
        text version_data_json
        varchar resume_template
        varchar resume_style
        varchar generation_status
        int ats_score
        text ai_feedback
    }

    CHAT_MESSAGES {
        bigint id PK
        bigint application_id FK
        bigint sender_id FK
        bigint recipient_id FK
        text content
        boolean is_read
        timestamp sent_at
    }
```

---

## 5. Security & Authentication Flows

UniHire implements a stateless authentication model backed by Spring Security, JJWT, and BCrypt.

### 5.1 Authentication & Token Refresh Flow

```mermaid
sequenceDiagram
    autonumber
    actor Client as User / Browser
    participant RLF as RateLimitFilter
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthService
    participant UserRepo as UserRepository
    participant TokenRepo as RefreshTokenRepository
    participant JwtSvc as JwtService
    participant PassEnc as BCryptPasswordEncoder

    Client->>RLF: POST /api/auth/login (email, password)
    RLF->>RLF: Check IP rate-limit token bucket in Redis
    RLF->>AuthCtrl: Forward validated request
    AuthCtrl->>AuthSvc: authenticate(loginRequest)
    AuthSvc->>UserRepo: findByEmail(email)
    UserRepo-->>AuthSvc: User Entity (hashed password, role, isLocked)

    AuthSvc->>PassEnc: matches(rawPassword, encodedPassword)
    PassEnc-->>AuthSvc: true / false

    alt Invalid Credentials or Account Locked
        AuthSvc-->>Client: 401 Unauthorized / 403 Forbidden
    else Valid Credentials
        AuthSvc->>JwtSvc: generateAccessToken(user)
        JwtSvc-->>AuthSvc: JWT Access Token (expires in 24 hours)
        AuthSvc->>TokenRepo: createRefreshToken(user)
        TokenRepo-->>AuthSvc: Opaque UUID Refresh Token (expires in 7 days)
        AuthSvc-->>Client: 200 OK { token, refreshToken, role, userId }
    end

    Note over Client,TokenRepo: Token Refresh Lifecycle
    Client->>AuthCtrl: POST /api/auth/refresh-token (refreshToken)
    AuthCtrl->>AuthSvc: refreshToken(request)
    AuthSvc->>TokenRepo: findByToken(refreshToken)
    TokenRepo-->>AuthSvc: RefreshToken record
    AuthSvc->>AuthSvc: Verify expiry date > now()
    AuthSvc->>JwtSvc: generateAccessToken(user)
    AuthSvc-->>Client: 200 OK { token, refreshToken }
```

### 5.2 Authorization Rules (`SecurityConfig.java`)
- **Public Endpoints (`permitAll`)**:
  - `/api/auth/**`: Login, registration, token refresh, OTP sending/verifying, password resets.
  - `/api/public/**`: Public job boards, job detail views, verified employer public profiles.
  - `/ws/**`: WebSocket initial handshake and SockJS info queries.
  - `/api/ai/test`: Development sanity check endpoints.
- **Admin Endpoints (`hasRole('ADMIN')`)**:
  - `/api/admin/**`: System-wide metrics, candidate locking/unlocking, employer verification approvals.
- **Authenticated Endpoints (`authenticated`)**:
  - All remaining endpoints (`/api/candidate/**`, `/api/employer/**`, `/api/jobs/**`, `/api/resume-builder/**`, `/api/recommendations/**`, `/api/chat/**`) require an active, cryptographically valid Bearer token.

---

## 6. End-to-End API Sequence Workflows

### 6.1 Asynchronous AI Resume Ingestion & Parsing Workflow

This flow traces how an uploaded binary resume is parsed, extracted, stored, vectorized, and cached:

```mermaid
sequenceDiagram
    autonumber
    actor Candidate as Candidate
    participant Controller as ResumeBuilderController
    participant Service as ResumeBuilderService
    participant DB as PostgreSQL Database
    participant JobRunr as JobRunr Background Queue
    participant RPS as ResumeParsingService
    participant Storage as StorageService (MinIO/R2)
    participant Tika as DocumentTextExtractor (Apache Tika)
    participant Gemini as Google Gemini AI (LLM)
    participant RecSvc as RecommendationService
    participant Cache as Redis CacheManager

    Candidate->>Controller: POST /api/resume-builder/upload (MultipartFile)
    Controller->>Service: initiateResumeParsing(candidateId, file)
    Service->>Storage: uploadFile(file)
    Storage-->>Service: storedFilename
    Service->>DB: INSERT ResumeParsingJob (STATUS: UPLOADED)
    Service->>JobRunr: enqueue(() -> parseResume(candidateId, storedFilename))
    Service-->>Controller: ResumeParsingJobDto
    Controller-->>Candidate: 200 OK (Job Tracking ID)

    Note over JobRunr,RPS: Asynchronous Execution in JobRunr Worker Thread
    JobRunr->>RPS: parseResume(candidateId, storedFilename)
    RPS->>DB: UPDATE CandidateProfile (parsingStatus = PROCESSING)
    RPS->>Storage: getFileStream(storedFilename)
    Storage-->>RPS: InputStream
    RPS->>Tika: extractText(fileStream)
    Tika-->>RPS: Extracted Raw Resume Text

    RPS->>Gemini: Prompt with BeanOutputConverter<ParsedResumeResponse>
    Gemini-->>RPS: Normalized JSON Response (Skills, Experience, Education)

    RPS->>DB: Overwrite Profile Collections (Educations, Experiences, Certifications)
    Note over RPS,DB: Dates normalized via DateUtils (Month-Year, no days)
    RPS->>DB: UPDATE CandidateProfile (parsingStatus = COMPLETED)

    RPS->>RecSvc: generateRecommendationsForCandidate(candidateId)
    RPS->>Cache: evictCandidateCaches(candidateId)
    Note over RPS,Cache: Evicts 'candidateProfiles', 'dashboardMetrics', 'recommendations'
```

### 6.2 Hybrid Search Flow (Lexical & Semantic Vector Matching)

This sequence traces how search requests dynamically fall back from OpenSearch to pgvector to relational SQL:

```mermaid
sequenceDiagram
    autonumber
    actor User as Job Seeker / Candidate
    participant PublicJobCtrl as PublicJobController
    participant PublicJobSvc as PublicJobService
    participant OpenSearch as OpenSearch Cluster (:9200)
    participant SemanticSvc as SemanticSearchService
    participant Gemini as Google Gemini Embeddings
    participant PgVector as PostgreSQL (pgvector Store)
    participant JobRepo as JobRepository (JPA Database)

    User->>PublicJobCtrl: GET /api/public/jobs?keyword=Engineer&location=Remote
    PublicJobCtrl->>PublicJobSvc: searchJobs(keyword, location, department, pageable)

    alt Keyword Present - Tier 1: OpenSearch
        PublicJobSvc->>OpenSearch: Execute Multi-Match Query (Title^2, Description)
        alt OpenSearch Available & Responds
            OpenSearch-->>PublicJobSvc: Document Hits & Relevance Scores
            PublicJobSvc->>JobRepo: findAllById(hitIds) (Hydrate Entity Models)
            PublicJobSvc-->>PublicJobCtrl: Pageable JobResponseDto Results
        else OpenSearch Down or Connection Timeout
            Note over PublicJobSvc,SemanticSvc: Failover to Tier 2: pgvector Semantic Search
            PublicJobSvc->>SemanticSvc: searchJobsBySemanticSimilarity("Engineer", limit)
            SemanticSvc->>Gemini: text-embedding-004("Engineer")
            Gemini-->>SemanticSvc: 3072-Dimensional Float Vector
            SemanticSvc->>PgVector: SELECT id FROM jobs ORDER BY embedding cosine_distance vector LIMIT 20
            PgVector-->>SemanticSvc: Matched Job IDs
            SemanticSvc->>JobRepo: findAllById(matchedIds)
            SemanticSvc-->>PublicJobCtrl: Semantic Matching JobResponseDto Results
        end
    else OpenSearch and pgvector Unavailable - Tier 3: JPA Database
        PublicJobSvc->>JobRepo: findAll(JobSpecification.withFilters(...), pageable)
        JobRepo-->>PublicJobSvc: SQL ILIKE Filtered Page
        PublicJobSvc-->>PublicJobCtrl: Pageable JobResponseDto Results
    end

    PublicJobCtrl-->>User: 200 OK (Job List with Metadata & Pagination)
```

---

## 7. Deployment & Infrastructure Topologies

### 7.1 Single-Node / Local Development Deployment Topology

In local development, the entire stateful dependency suite is managed via `docker-compose.yml`, while developers execute the Spring Boot app and Vite server natively on the host:

```mermaid
graph TD
    subgraph Host["Developer Workstation (Windows / Linux / macOS)"]
        Browser["Web Browser (:5173)"]
        Vite["Vite Dev Server (:5173)<br/>Node.js 20+"]
        SpringBoot["UniHire Spring Boot App (:8080)<br/>Java 27 / Spring Boot 4.1.1"]
        JobRunrDash["JobRunr Web Console (:8000)"]
    end

    subgraph DockerNetwork["Docker Desktop / Compose Network (unihire-network)"]
        C_PG["unihire-postgres (:5432)<br/>Image: ankane/pgvector:v0.5.1"]
        C_RD["unihire-redis (:6379)<br/>Image: redis:7-alpine"]
        C_OS["unihire-opensearch (:9200)<br/>Image: opensearchproject/opensearch:2.19.0"]
        C_MINIO["unihire-minio (:9000, :9001)<br/>Image: minio/minio:latest"]
    end

    subgraph Cloud["External Cloud Services"]
        GeminiAPI["Google Gemini Generative AI<br/>generativelanguage.googleapis.com"]
    end

    Browser -->|HTTP / Hot Reload| Vite
    Browser -->|API Calls :8080| SpringBoot
    Browser -->|Dashboard :8000| JobRunrDash

    SpringBoot -->|JDBC :5432| C_PG
    SpringBoot -->|Lettuce :6379| C_RD
    SpringBoot -->|REST :9200| C_OS
    SpringBoot -->|S3 SDK :9000| C_MINIO
    SpringBoot -->|HTTPS REST| GeminiAPI
```

### 7.2 Production Enterprise Cloud Topology

In high-availability production, UniHire deploys across container orchestration clusters (AWS ECS / Kubernetes) with managed database services:

```mermaid
graph TD
    subgraph Edge["Global Edge Tier (Cloudflare)"]
        DNS["Cloudflare DNS & DDoS Shield"]
        CDN["Cloudflare CDN (Static UI Assets)"]
        WAF["Web Application Firewall (WAF)"]
        R2["Cloudflare R2 Object Storage"]
    end

    subgraph VPC["Virtual Private Cloud (VPC)"]
        ALB["Application Load Balancer (ALB / Ingress)"]

        subgraph ContainerCluster["Application Cluster (Kubernetes / ECS)"]
            Node1["UniHire Pod / Instance 1"]
            Node2["UniHire Pod / Instance 2"]
            NodeN["UniHire Pod / Instance N"]
        end

        subgraph ManagedData["Managed State & Persistence Cluster"]
            RDS_PG[("Amazon RDS PostgreSQL 16+<br/>pgvector Extension Enabled<br/>(Multi-AZ Primary + Read Replica)")]
            AOSS[("Amazon OpenSearch Service<br/>(Clustered Master + Data Nodes)")]
            ELC[("Amazon ElastiCache for Redis<br/>(Multi-AZ with Auto-Failover)")]
        end
    end

    DNS --> WAF --> ALB
    DNS --> CDN
    ALB --> Node1
    ALB --> Node2
    ALB --> NodeN

    Node1 --> RDS_PG
    Node2 --> RDS_PG
    NodeN --> RDS_PG

    Node1 --> AOSS
    Node2 --> AOSS
    NodeN --> AOSS

    Node1 --> ELC
    Node2 --> ELC
    NodeN --> ELC

    Node1 --> R2
    Node2 --> R2
    NodeN --> R2

    Node1 -->|Outbound HTTPS| GeminiCloud["Google Cloud Vertex AI / Gemini API"]
    Node2 -->|Outbound HTTPS| GeminiCloud
    NodeN -->|Outbound HTTPS| GeminiCloud
```

---

## 8. Cross-Cutting Concerns & Technical Invariants

### 8.1 Date Representation Invariant
- **Rule**: All AI-extracted, user-edited, and resume-generated start and end dates must omit days and follow the normalized **Month-Year** format (e.g., `"Sept-2026"`, `"May-2023"`), with ongoing tenures represented strictly as `"Present"`.
- **Implementation**: Enforced via `DateUtils.formatMonthYear(String)` and `DateUtils.formatDateRange(String, String)`.

### 8.2 Resilient Fail-Open Caching & Rate Limiting
- **Rule**: A transient failure of the Redis cluster must never block business transactions or crash the application.
- **Implementation**:
  - `RateLimitFilter` wraps Redis access in a `try-catch` block, logging a warning and proceeding with filter chain execution if Redis is unavailable.
  - `RedisCacheConfig` registers a custom `CacheErrorHandler`, converting Redis read/write timeouts into cache-misses and executing direct database lookups.

### 8.3 Hybrid Search Graceful Degradation
- **Rule**: Job searches must succeed even if search indices drift or OpenSearch experiences downtime.
- **Implementation**: `PublicJobService` implements fallback cascading: OpenSearch $\rightarrow$ pgvector semantic similarity $\rightarrow$ JPA database specification queries.

---

## 9. Code Reference & Source Traceability Matrix

| Architectural Component | Primary Source File(s) | Primary Configuration Key(s) |
| :--- | :--- | :--- |
| **Security Filter Chain** | `src/main/java/com/unihire/config/SecurityConfig.java` | `jwt.secret`, `jwt.expiration` |
| **JWT Verification Filter** | `src/main/java/com/unihire/config/JwtFilter.java` | `jwt.secret` |
| **Token-Bucket Rate Limiter** | `src/main/java/com/unihire/config/RateLimitFilter.java` | `spring.data.redis.host`, `spring.data.redis.port` |
| **OpenSearch REST Client** | `src/main/java/com/unihire/config/OpenSearchConfig.java` | `spring.opensearch.uris`, `spring.opensearch.username` |
| **Redis Cache Fallback** | `src/main/java/com/unihire/config/RedisCacheConfig.java` | `spring.data.redis.*` |
| **JobRunr Server & Dashboard** | `src/main/java/com/unihire/config/JobRunrConfig.java` | `jobrunr.background-job-server.enabled`, `jobrunr.dashboard.port` |
| **WebSocket STOMP Interceptor** | `src/main/java/com/unihire/config/JwtChannelInterceptor.java` | `jwt.secret` |
| **Resume Extraction Service** | `src/main/java/com/unihire/services/ResumeParsingService.java` | `spring.ai.google.genai.chat.options.model` |
| **Resume Builder & Customization** | `src/main/java/com/unihire/services/ResumeBuilderService.java` | `resume.ai.provider` |
| **Semantic Vector Matching** | `src/main/java/com/unihire/services/SemanticSearchService.java` | `spring.ai.vectorstore.pgvector.*` |
| **Frontend Resume Rendering** | `apps/uniHire-ui/src/components/candidate/ResumePreviewRenderer.tsx` | N/A (Client TypeScript Engine) |
