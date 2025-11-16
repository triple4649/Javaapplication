了解です。
ここでは **「Spring Boot 3.4.x / Spring Security 6+ の最新方式で JWT 認証を実装する最小構成」** を
**初心者でもコピペで動くレベル**に落とし込んで解説します。

以下の構成は **最新の Spring Security DSL（SecurityFilterChain）対応・非推奨APIなし**です。

---

# 🔥 Spring Security（6+）で JWT 認証を実装するには？

JWT 認証では次の流れになります：

```
[1] /auth/login に username/password を POST
→ JWT を発行して返す

[2] クライアントは以降、
    Authorization: Bearer <token>
    をヘッダにつけてアクセス

[3] サーバ側の JWT フィルタが Token を検証し、
    SecurityContext に User情報をセット

[4] Controller は認証済みとして動く
```

---

# ✅ 1. 依存関係（build.gradle）

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.11.5'
}
```

---

# ✅ 2. JWT Utility（生成 & 検証）

`JwtService.java`

```java
package com.example.demo.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    private static final long EXPIRATION = 1000 * 60 * 30; // 30 minutes
    private final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(key)
                .compact();
    }

    public String validateAndGetUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}
```

---

# ✅ 3. JWT 認証フィルタ（Password不要、JWTだけで認証）

`JwtAuthFilter.java`

```java
package com.example.demo.jwt;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthFilter extends GenericFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void doFilter(
            @NonNull ServletRequest request,
            @NonNull ServletResponse response,
            @NonNull FilterChain chain
    ) throws IOException, ServletException {

        HttpServletRequest http = (HttpServletRequest) request;
        String authHeader = http.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);

            try {
                String username = jwtService.validateAndGetUsername(jwt);

                User principal = User.withUsername(username)
                        .password("") // パスワード不要
                        .authorities("USER")
                        .build();

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()
                        );

                auth.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(http)
                );

                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception e) {
                ((HttpServletResponse) response).sendError(401, "Invalid JWT");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
```

---

# ✅ 4. SecurityConfig（JWT フィルタ設定）

`SecurityConfig.java`

```java
package com.example.demo;

import com.example.demo.jwt.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

---

# ✅ 5. ログイン API（JWT を返す）

`AuthController.java`

```java
package com.example.demo;

import com.example.demo.jwt.JwtService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    record LoginRequest(String username, String password) {}

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest req) {

        // ★ 本来は DBなどでユーザ検証する
        if (!req.username().equals("admin") || !req.password().equals("pass123")) {
            throw new RuntimeException("Invalid credentials");
        }

        return jwtService.generateToken(req.username());
    }
}
```

---

# ✅ 6. 動作確認用 API

`HelloController.java`

```java
package com.example.demo;

import org.springframework.web.bind.annotation.*;

@RestController
public class HelloController {

    @GetMapping("/public")
    public String publicApi() {
        return "Public OK";
    }

    @GetMapping("/secure")
    public String secureApi() {
        return "Secure OK (JWT required)";
    }
}
```

---

# ✅ 7. 動作確認

## ① JWT を取得（ログイン）

```
POST http://localhost:8080/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "pass123"
}
```

→ 返ってくる例：

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImV4cCI6...
```

---

## ② JWT を使って認証付き API にアクセス

```
GET http://localhost:8080/secure
Authorization: Bearer <JWT>
```

---

# 🎯 まとめ（最短）

* **/auth/login** で username/password を受け取り JWT を返す
* **JWT フィルタ** が毎リクエストで Authorization ヘッダを検証
* 認証 OK なら SecurityContext にユーザ情報をセット
* `/secure` などは JWT が無いとアクセス不可

---

# 🔥 必要なら次を作ることも可能

* Refresh Token 対応
* ロール (ROLE_ADMIN / ROLE_USER)
* UserDetailsService + DB連携版
* JWT を Cookie と HttpOnly で返すセキュア構成
* SPA(Vue/React) との連携テンプレ

どれを作る？
