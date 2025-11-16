# Copilot Instructions: demoJWT

## Project Overview

**demoJWT** is a Spring Boot 3.4.x application demonstrating **stateless JWT (JSON Web Token) authentication** with Spring Security 6+. The architecture uses a minimal, modern approach without deprecated APIs.

### Technology Stack
- **Framework**: Spring Boot 3.4.1 with Spring Security 6+
- **Build**: Gradle
- **Authentication**: JJWT 0.11.5 for JWT generation/validation
- **Java**: Modern Java with Records for DTOs

---

## Architecture: JWT Authentication Flow

### Data Flow (High Level)
```
[Client] --POST /auth/login--→ [AuthController] 
                                    ↓ (validates credentials)
                              [JwtService.generateToken()]
                                    ↓
                        [return JWT token to client]
                                    ↓
[Client] --GET /secure + Bearer JWT--→ [SecurityFilterChain]
                                          ↓ (JwtAuthFilter)
                                    [validate JWT]
                                          ↓
                                 [SecurityContext ← Auth]
                                          ↓
                                   [Controller works]
```

### Key Components

1. **`AuthController.java`** (`/auth` endpoints)
   - `/auth/login` endpoint accepts `LoginRequest` (username, password)
   - Hardcoded credentials: `admin` / `pass123` (TODO: use DB)
   - Returns JWT token string on success, throws exception on failure
   - Currently **not password-encoded** (see TODO in code)

2. **`JwtService.java`** (Core JWT logic)
   - `generateToken(username)`: Creates HS256-signed JWT with 30-minute expiration
   - `validateAndGetUsername(token)`: Parses & validates token, extracts username
   - Uses symmetric key generated via `Keys.secretKeyFor(SignatureAlgorithm.HS256)`
   - **Note**: Key is regenerated on each app restart (stateless design, tokens invalidate on restart)

3. **`JwtAuthFilter.java`** (Servlet filter for JWT validation)
   - Extends `GenericFilter` (not deprecated `OncePerRequestFilter`)
   - Extracts `Authorization: Bearer <jwt>` header
   - On valid JWT: Creates `UsernamePasswordAuthenticationToken` and sets in `SecurityContextHolder`
   - On invalid JWT: Sends 401 Unauthorized and stops chain
   - If no JWT: Passes chain without authentication (anonymous request)
   - Uses **empty password** in UserDetails (password-based auth not used)

4. **`SecurityConfig.java`** (Spring Security configuration)
   - Disables CSRF (stateless JWT, no cookies)
   - Rules: `/auth/login` permitAll, all other endpoints require authentication
   - Adds `JwtAuthFilter` **before** `UsernamePasswordAuthenticationFilter`
   - Uses modern lambda DSL (not WebSecurityConfigurerAdapter)

---

## Critical Concepts for Agents

### ⚠️ SecurityContext Lifecycle (Thread-Local Scoped)
- Each HTTP request gets its own isolated `SecurityContext` (via ThreadLocal)
- **JwtAuthFilter MUST set authentication on each request** (stateless)
- Request processing ends → `SecurityContextHolder.clearContext()` cleans up
- No session persistence; JWT is the ONLY authentication mechanism

### ⚠️ Filter Chain Order Matters
- `JwtAuthFilter` added via `.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`
- Must run before `UsernamePasswordAuthenticationFilter` to intercept JWT before password auth
- Invalid JWT → stop chain with 401 (prevents fallthrough to other auth filters)

### ⚠️ Credential Validation is Stubbed
- `AuthController.login()` checks hardcoded `admin` / `pass123`
- Actual production: Should call UserService → database lookup + password encoding (e.g., BCryptPasswordEncoder)
- Comment marks this: `// ★ 本来は DBなどでユーザ検証する`

### ⚠️ JWT Key Management (Single Instance Problem)
- Key is instance variable: `private final Key key = Keys.secretKeyFor(...)`
- Generated once per JwtService bean startup
- **Implication**: On app restart, all existing tokens become invalid (no persistent key)
- Production fix: Load key from `application.properties` or secret store

---

## Developer Workflows

### Build & Run
```bash
# Build with Gradle wrapper
./gradlew build

# Run application (starts on http://localhost:8080)
./gradlew bootRun

# Run tests
./gradlew test
```

### Test JWT Manually
```bash
# 1. Get token
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"pass123"}'

# 2. Access protected endpoint with token
curl -H "Authorization: Bearer <TOKEN_HERE>" \
  http://localhost:8080/secure
```

### Common Test Assertions
- Invalid credentials → RuntimeException (logs 400 or 500 depending on error handler)
- Missing JWT on protected endpoint → 401 Unauthorized  
- Expired JWT → 401 Unauthorized (JwtException caught in filter)
- Valid JWT → Request proceeds, SecurityContext populated

---

## Codebase Patterns

### Dependency Injection Pattern
All components use constructor injection (no `@Autowired` fields):
```java
public AuthController(JwtService jwtService) {
    this.jwtService = jwtService;
}
```
**Convention**: Always use constructor injection for testability and immutability.

### Record-based DTOs
```java
record LoginRequest(String username, String password) {}
```
**Convention**: Use Java Records for simple request/response objects (no Lombok).

### Service Layer Organization
- `@Service` beans handle business logic (JWT operations, user validation)
- Controllers delegate to services
- Filters use services but operate at servlet level (not business logic)

### Exception Handling
- Currently **minimal** (RuntimeException thrown for invalid credentials)
- Production TODO: Create custom `InvalidCredentialsException`, `JwtValidationException`
- No global `@ControllerAdvice` present (needs implementation for production)

---

## Integration Points & Dependencies

### External Libraries (from build.gradle)
- `spring-boot-starter-security`: Spring Security 6+ with filter chain DSL
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson`: JWT library (0.11.5)
- `spring-boot-starter-web`: REST controller support

### Endpoint Contract
| Endpoint        | Method | Auth Required | Request Body          | Response                |
|-----------------|--------|---------------|-----------------------|-------------------------|
| `/auth/login`   | POST   | No            | `{username, password}`| JWT token string or 400 |
| `/secure`       | GET    | **Yes (JWT)** | —                     | String message or 401   |
| `/public`       | GET    | No            | —                     | String message          |

**Note**: `/secure` and `/public` endpoints do not exist yet in the provided codebase (likely placeholder for testing).

---

## Important Gotchas & Production TODOs

### 🚨 Security Gaps (Mark "TODO" before adding features)
1. **Password Encoding**: Currently no BCryptPasswordEncoder → never use with real passwords
2. **User Lookup**: Hardcoded credentials only → implement UserDetailsService + repository
3. **JWT Key Rotation**: Key lost on restart → persist in properties or vault
4. **Token Refresh**: No refresh token mechanism (30-min expiry only)
5. **Role-Based Access**: All authenticated users get `"USER"` authority (no fine-grained access)
6. **Error Handling**: No global exception handler → implement `@ControllerAdvice`

### 📋 File Locations Reference
- Main entry: `src/main/java/com/example/demo_jwt/DemoApplication.java`
- Controllers: `src/main/java/com/example/demo_jwt/controller/`
- Services: `src/main/java/com/example/demo_jwt/service/`
- Filters: `src/main/java/com/example/demo_jwt/filter/`
- Config: `src/main/java/com/example/demo_jwt/config/`
- Tests: `src/test/java/com/example/demo_security/DemoApplicationTests.java` (note: different package)
- Docs: `md/JWT.md` (detailed explanation of implementation)

---

## Quick Reference

**When adding features, follow these patterns:**
- New REST endpoints → add to `@RestController` with `@PostMapping`/`@GetMapping`
- New authentication logic → extend `JwtService`
- New security rules → modify `SecurityConfig.filterChain()`
- New data validation → add to `FilterChain` or create filter
- Testing → use `@SpringBootTest` with `@MockMvc` (see existing test structure)

**When debugging auth failures:**
1. Check SecurityContext exists (set breakpoint in JwtAuthFilter)
2. Verify JWT format: must be `Authorization: Bearer <token>` (exact spacing)
3. Check token expiration (30 minutes from generation)
4. Confirm filter order: JwtAuthFilter must run before UsernamePasswordAuthenticationFilter
