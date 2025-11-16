結論から言うと **Spring Security の SecurityContext を「セッション単位」で固定し、リクエストごとに引き継がせることは可能** ですが、**標準では「スレッドローカル（request 単位）＋ セッションによる永続化」で構成されている**ため、「完全にセッションスコープの Bean として扱う」ことはできません。

ただし、**セッション単位で SecurityContext を保持する仕組み**は Spring Security がすでに用意しており、カスタムする必要はほとんどありません。

---

# ✅ 結論

### Spring Security はデフォルトで以下のように動く：

1. **SecurityContextHolder**

   * デフォルトは **MODE_THREADLOCAL**
   * リクエスト開始時に SecurityContextRepository から context をロード
   * リクエスト終了時に ThreadLocal をクリア

2. **SecurityContextRepository による永続化**

   * デフォルトは **HttpSessionSecurityContextRepository**
   * セッションに `SPRING_SECURITY_CONTEXT` として SecurityContext を保持する
   * → **実質「セッション単位のライフサイクル」になっている**

### ▶ 結論：

**SecurityContext は "セッションに永続化された状態" を request ごとに ThreadLocal にコピーして使っている。**
**つまり、Session スコープのように振る舞っている。**

---

# ❓「完全に Session スコープの Bean にできる？」→ 基本的には NO

Spring Security が「ユーザーごとに独立した SecurityContext を session に保存し、それを thread-local キャッシュとして扱う」設計になっているため、以下は原則できません：

* SecurityContext を @SessionScope Bean として扱う
* SecurityContextHolder から直接 SessionScope のインスタンスを参照させる

これは SecurityFilterChain の早い段階で動く処理なので、Spring の一般的スコープ管理とは独立しています。

---

# 🔎 仕組みを図解

```
             +-------------------------------+
 Request --> | SecurityContextRepository     |
             | (HttpSession)                 |
             +---------------+---------------+
                             |
                             v
                 +------------------------+
                 | SecurityContextHolder  |
                 | (ThreadLocal per req) |
                 +------------------------+
                             |
                             v
                 認証情報にアクセス(Authentication)
```

* リクエストが来るたびに session からコピー
* リクエストが終わったら ThreadLocal をクリア
* 変更があれば session に保存

---

# 🛠 セッション単位の SecurityContext をさらに明示的に使いたい場合

### ✔ 方法 1: `MODE_INHERITABLETHREADLOCAL` へ変更

非同期処理で子スレッドにコンテキストを引き継ぎたい場合：

```java
SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
```

---

### ✔ 方法 2: セッションから直接 Authentication を参照する

```java
Authentication auth = (Authentication)
    request.getSession()
        .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
        .getAuthentication();
```

---

### ✔ 方法 3: セッション上に独自の「UserContext」を持たせる

```java
@Component
@SessionScope
public class UserSessionContext {
    private String userId;
    private String role;
}
```

Spring Security の SecurityContext とは別に、
「アプリ固有のユーザーセッション情報」を SessionScope で管理できます。

---

# 📌 まとめ

| やりたいこと                                    | 可能？           | 理由                                   |
| ----------------------------------------- | ------------- | ------------------------------------ |
| SecurityContext を Session 単位で永続化          | **可能（デフォルト）** | HttpSessionSecurityContextRepository |
| SecurityContext を SessionScope Bean として扱う | **不可**        | SecurityFilterChain の設計              |
| セッション情報をアプリ用に持つ                           | **可能**        | @SessionScope を自作                    |

---

# 🚀 最終回答（簡潔）

Spring Security の SecurityContext はすでに **実質セッション単位のライフサイクル**で動作します。
ただし、**内部的には ThreadLocal（request）＋ Session（永続化）**で管理されており、
SecurityContext を完全な SessionScope の Bean に置き換えることはできません。

もしやりたいことが「ユーザーごとの状態をセッションで管理したい」なら
→ **独自の SessionScope Bean を作るアプローチ**が最適です。

---

必要なら、その「セッション用ユーザ状態コンテキスト」の実装例も作ります。
