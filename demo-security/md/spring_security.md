以下に、**Spring Boot 3.4.x + Spring Security の完全テンプレート構築手順**を
**最初の ZIP 生成 → セキュリティ設定 → コントローラ → Gradle 実行**まで
すべて Markdown でまとめた “完全版テンプレ” を提示します。

---

# Spring Boot 3.4.x + Spring Security

# 完全テンプレート構築ガイド（Gradle版）

このドキュメントは以下を前提に、**最小構成の Spring Security プロジェクトをゼロから構築します**。

* Spring Boot **3.4.x**
* Gradle
* Java 17 +
* VS Code / IntelliJ どちらでも動く

---

# 1. プロジェクト生成（Spring Initializr / curl）

Spring Boot 3.4.x を指定し、**確実に ZIP を取得できるコマンド**：

```bash
curl -L -o demo-security.zip \
  "https://start.spring.io/starter.zip?type=gradle-project&language=java&dependencies=web,security&bootVersion=3.4.1&javaVersion=17&groupId=com.example&artifactId=demo-security"
```

解凍：

```bash
unzip demo-security.zip
cd demo-security
```

生成される構成：

```
demo-security/
 ├─ build.gradle
 ├─ settings.gradle
 ├─ src/main/java/com/example/demo/DemoSecurityApplication.java
 └─ src/main/resources/application.properties
```

---

# 2. build.gradle（確認）

Spring Security が追加されていることを確認：

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.1'
    id 'io.spring.dependency-management' version '1.1.5'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

---

# 3. アプリケーションエントリ

`src/main/java/com/example/demo/DemoSecurityApplication.java`

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoSecurityApplication.class, args);
    }
}
```

---

# 4. SecurityConfig（Spring Security 6/7 方式）

Spring Boot 3.4.x では `SecurityFilterChain` を使うのが正しい構成。

`src/main/java/com/example/demo/SecurityConfig.java`

```java
package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())   // REST API向け：CSRF無効化
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public").permitAll()  // 認証不要
                .anyRequest().authenticated()            // それ以外は認証必須
            )
            .httpBasic();  // Basic認証

        return http.build();
    }
}
```

---

# 5. Controller（動作確認用）

`src/main/java/com/example/demo/HelloController.java`

```java
package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/public")
    public String publicApi() {
        return "Public OK";
    }

    @GetMapping("/secure")
    public String secureApi() {
        return "Secure OK (auth required)";
    }
}
```

---

# 6. application.properties（Basic認証ユーザ）

`src/main/resources/application.properties`

```properties
spring.security.user.name=admin
spring.security.user.password=pass123
```

---

# 7. 起動（Gradle）

```bash
./gradlew bootRun
```

Windows:

```bat
gradlew.bat bootRun
```

---

# 8. 動作確認

### 認証不要

```
http://localhost:8080/public
→ "Public OK"
```

### 認証必要（Basic認証）

```
http://localhost:8080/secure
ユーザ: admin
パスワード: pass123
```

---

# 9. これで完成！

これで以下を満たした **Spring Boot 3.4.x + Spring Security 最小 REST 構成**が完成：

* Gradle で構築
* CSRF 無効化（REST向け）
* `/public` は認証なし
* `/secure` は Basic 認証
* 設定は最新の SecurityFilterChain 方式（Spring Security 6/7対応）

---

# 🔥 次に追加可能なテンプレ（必要なら生成します）

* JWT 認証版テンプレ
* OAuth2 (Google / GitHub) ログイン
* Session-less REST API Security Config
* フォームログイン（HTML + CSRF 有効）版
* ロールベース認可 (ROLE_USER / ROLE_ADMIN)

---

必要なバージョン・構成に合わせて、
**あなたのプロジェクトに最適化した Spring Security テンプレ**も作成できます。
