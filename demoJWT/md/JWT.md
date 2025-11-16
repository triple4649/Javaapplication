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


JWT が無いと `/secure` などにアクセスできない理由は、
**Spring Security の認可ルール（authorizeHttpRequests）と、JWT フィルタの動作の組み合わせ**によって実現されています。

以下、**内部で何が起きているかを完全に図解で説明**します。

---

# 🔥 結論：アクセス不可を実現している仕組みは 2 つ

## **① Spring Security のルールで “JWT 以外は許可しない” と宣言している**

SecurityConfig の以下：

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/auth/login").permitAll()
    .requestMatchers("/public").permitAll()
    .anyRequest().authenticated()
)
```

この意味：

| ルール                   | 意味                                       |
| --------------------- | ---------------------------------------- |
| `/auth/login`         | 認証不要（JWT 不要）                             |
| `/public`             | 認証不要（JWT 不要）                             |
| それ以外 **anyRequest()** | **認証が必要（＝SecurityContext に認証情報がなければ拒否）** |

つまり JWT 以前に、**SecurityContext に認証済みユーザーが存在しなければ全て拒否**する構造になっている。

---

## **② JWT フィルタが “JWT があれば SecurityContext に User をセットする”**

JWT フィルタ（JwtAuthFilter）の役割は：

* "Authorization: Bearer XXX" がある → JWT を検証してユーザとして認証させる
* 無い → **何もしない**（＝未認証のまま）

実際のコード：

```java
if (authHeader != null && authHeader.startsWith("Bearer ")) {
    String jwt = authHeader.substring(7);
    String username = jwtService.validateAndGetUsername(jwt);

    User principal = User.withUsername(username)
            .password("")
            .authorities("USER")
            .build();

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            principal, null, principal.getAuthorities()
        );

    SecurityContextHolder.getContext().setAuthentication(auth);
}
```

つまり：

* JWT がある & 正しい → `SecurityContext` に認証済みユーザをセット
* JWT が無い → SecurityContext は **空のまま（＝未認証）**

---

# 🔒 だから、最終的にはどうなるのか？

## ✔ JWT ありのケース

```
Authorization: Bearer xxxxx
→ jwtAuthFilter がユーザを SecurityContext にセット
→ anyRequest().authenticated() を満たす
→ /secure にアクセス可能
```

## ✔ JWT なしのケース

```
Authorization ヘッダなし
→ jwtAuthFilter はユーザをセットしない
→ SecurityContext は空（＝未認証）
→ anyRequest().authenticated() を満たさない
→ Spring Security が自動で 401 Unauthorized
```

---

# 🎨 全体の流れ（図解）

```
Request → SecurityFilterChain
            ↓
       JwtAuthFilter
           ├─ JWT あり → SecurityContext に認証情報セット
           └─ JWT なし → 何もしない（未認証のまま）
            ↓
    authorizeHttpRequests
           ├─ permitAll のパス？
           │      └→ OK
           └─ それ以外は authenticated() 必須
                    ├─ 認証済み？ → OK
                    └─ 未認証 → 401（アクセス不可）
```

---

# 🎯 最短まとめ

### JWT が無いとアクセス不可になる理由は：

1. **anyRequest().authenticated()**
   → 「認証必須」というルール

2. **JWT フィルタが SecurityContext をセットする唯一の手段**
   → JWT が無いと SecurityContext は “空” のまま

よって **JWT を送らない限り、認証必須の API には絶対にアクセスできない設計**になっている。

---

必要なら、
**“403 Forbidden と 401 Unauthorized の違い”** や
**“JWT の有効期限切れ時の挙動”** もまとめます。

`SecurityContext` の「スコープ（どの範囲で生きるのか）」は
Spring Security の認証モデルを理解する上で **最重要ポイント**です。

結論から書くと：

---

# 🔥 **結論：SecurityContext のスコープは “HTTP リクエスト単位（ThreadLocal 単位）”**

つまり、

* **各リクエストごとに独立した SecurityContext が作られる**
* 各リクエストの間で SecurityContext は共有されない
* 認証情報は **Servlet フィルタチェーンの中だけ** 有効
* リクエスト処理が終わると **破棄される**

これが Spring Security の標準的な動作です。

---

# 🧠 **では実際どうなっているの？ → ThreadLocal**

Spring Security は `SecurityContextHolder` の中に
**ThreadLocal で SecurityContext を保持**しています。

```java
private static final ThreadLocal<SecurityContext> contextHolder =
        new ThreadLocal<>();
```

つまり、

> **1 HTTP リクエスト = 1 Thread = 1 SecurityContext**

という構造。

---

# 🔍 **なぜ ThreadLocal なの？**

理由：

* サーブレットコンテナ（Tomcat/Jetty）が **1 リクエストごとに 1 スレッドを割り当てる**
* その間、認証情報を安全に保持する必要がある
* リクエスト間で情報が漏れないようにするため

だから各リクエストは完全に独立している。

---

# 🔒 **SecurityContext のライフサイクル**

以下の順で動く：

```
① リクエスト開始
    → SecurityContextHolder が「空の SecurityContext」を作る

② 認証フィルタ（例：JwtAuthFilter）が認証に成功
    → SecurityContext に Authentication をセット

③ コントローラまでの間、認証情報が保持される

④ レスポンス完了
    → SecurityContextHolder.clearContext() が呼ばれ ThreadLocal を削除
```

---

# 🧪**実験で理解できる例**

例えば `/secure` にアクセスするとき、
`JwtAuthFilter` が `SecurityContextHolder.getContext().setAuthentication(auth)` を呼ぶ。

しかし **次のリクエスト**では、再び SecurityContext は空になる：

```
Request A: JWT OK → context にユーザーがセット
Request B: JWT なし → context は空（アクセス拒否）
```

つまり **ステートレス（状態を持たない）**。

---

# 🧩 **では、セッションと関係ある？**

結論：

## ✔ JWT 認証では SecurityContext は **セッションを使わない（完全ステートレス）**

* OAuth2 ログインやフォームログインの場合：セッションに保存される
* JWT 認証の場合：セッションは使わず **毎リクエストで JWT を検証**

今作っている JWT 認証構成では：

```
sessionCreationPolicy = STATELESS
```

を暗黙的に使っているため、
**SecurityContext は ThreadLocal 内だけに存在する transient（瞬間的）なもの。**

---

# 🎯 **最短まとめ**

### ✔ SecurityContext のスコープは？

> **HTTP リクエスト単位（実体は ThreadLocal）。**

### ✔ リクエストが終わるとどうなる？

> **SecurityContextHolder.clearContext() により破棄される。**

### ✔ リクエスト間で共有される？

> **されない。完全に独立。**

### ✔ JWT 認証との関係は？

> JWT フィルタが毎回 SecurityContext に認証情報を入れる
> → つまりリクエストごとにログインする方式（ステートレス）

---

必要なら次に：

* **SecurityContext が ThreadLocal で保持されるコードの解説**
* **Reactive WebFlux の SecurityContext（ThreadLocal ではない）**
* **セッション（STATEFUL）方式との比較図**

などもまとめられます。
