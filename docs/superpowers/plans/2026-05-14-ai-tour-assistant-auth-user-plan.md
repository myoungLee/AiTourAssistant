# AI 旅游助手认证与用户资料 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在基础工程之上实现注册、登录、退出登录、JWT 鉴权、Redis 登录态辅助和用户资料管理。

**Architecture:** 认证模块采用 Spring Security + JWT。MySQL 保存用户和资料，Redis 保存刷新令牌、黑名单和资料缓存。Controller 只处理请求响应，认证逻辑放在 `application` 和 `infrastructure/security`。

**Tech Stack:** Spring Boot 3、Spring Security、JWT、BCrypt、Redis、MyBatis-Plus、Jakarta Validation、JUnit 5。

---

## 前置条件

- 已完成基础工程计划。
- `users` 和 `user_profile` 表已由 Flyway 创建。
- 后端可以运行 `mvn test`。

## 文件结构规划

```text
backend/src/main/java/com/aitour/
  api/AuthController.java
  api/UserController.java
  application/AuthService.java
  application/UserProfileService.java
  domain/User.java
  domain/UserProfile.java
  domain/UserStatus.java
  infrastructure/persistence/UserMapper.java
  infrastructure/persistence/UserProfileMapper.java
  infrastructure/security/JwtAuthenticationFilter.java
  infrastructure/security/JwtProperties.java
  infrastructure/security/JwtTokenService.java
  infrastructure/security/SecurityConfig.java
  infrastructure/security/CurrentUser.java
  infrastructure/exception/ApiException.java
  infrastructure/exception/GlobalExceptionHandler.java
  api/dto/AuthDtos.java
  api/dto/UserDtos.java
```

## Task 1: 增加认证依赖和配置

**Files:**

- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 在 `backend/pom.xml` 增加依赖**

在 `<dependencies>` 中加入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 2: 在 `application.yml` 增加 JWT 配置**

追加：

```yaml
security:
  jwt:
    issuer: aitour
    secret: ${JWT_SECRET:aitour-local-development-secret-must-be-32-bytes}
    access-token-minutes: 120
    refresh-token-days: 14
```

- [ ] **Step 3: 运行后端测试**

Run:

```bash
cd backend
mvn test
```

Expected:

```text
BUILD SUCCESS
```

## Task 2: 用户领域模型和 Mapper

**Files:**

- Create: `backend/src/main/java/com/aitour/domain/UserStatus.java`
- Create: `backend/src/main/java/com/aitour/domain/User.java`
- Create: `backend/src/main/java/com/aitour/domain/UserProfile.java`
- Create: `backend/src/main/java/com/aitour/infrastructure/persistence/UserMapper.java`
- Create: `backend/src/main/java/com/aitour/infrastructure/persistence/UserProfileMapper.java`

- [ ] **Step 1: 创建用户状态枚举**

```java
package com.aitour.domain;

public enum UserStatus {
    ENABLED,
    DISABLED
}
```

- [ ] **Step 2: 创建 `User` 实体**

```java
package com.aitour.domain;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("users")
public class User {
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private String email;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isEnabled() {
        return UserStatus.ENABLED.name().equals(status);
    }

    // 生成标准 getter 和 setter
}
```

- [ ] **Step 3: 创建 `UserProfile` 实体**

```java
package com.aitour.domain;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("user_profile")
public class UserProfile {
    private Long id;
    private Long userId;
    private String gender;
    private String ageRange;
    private String travelStyle;
    private String defaultBudgetLevel;
    private String preferredTransport;
    private String preferencesJson;
    private Instant createdAt;
    private Instant updatedAt;

    // 生成标准 getter 和 setter
}
```

- [ ] **Step 4: 创建 Mapper**

`UserMapper.java`：

```java
package com.aitour.infrastructure.persistence;

import com.aitour.domain.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

`UserProfileMapper.java`：

```java
package com.aitour.infrastructure.persistence;

import com.aitour.domain.UserProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
```

## Task 3: DTO、异常和统一响应

**Files:**

- Create: `backend/src/main/java/com/aitour/api/dto/AuthDtos.java`
- Create: `backend/src/main/java/com/aitour/api/dto/UserDtos.java`
- Create: `backend/src/main/java/com/aitour/infrastructure/exception/ApiException.java`
- Create: `backend/src/main/java/com/aitour/infrastructure/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 创建认证 DTO**

```java
package com.aitour.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 64) String nickname
    ) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            Long userId,
            String username,
            String nickname
    ) {
    }
}
```

- [ ] **Step 2: 创建用户 DTO**

```java
package com.aitour.api.dto;

import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {
    }

    public record CurrentUserResponse(
            Long id,
            String username,
            String nickname,
            String avatarUrl,
            String phone,
            String email
    ) {
    }

    public record ProfileResponse(
            String gender,
            String ageRange,
            String travelStyle,
            String defaultBudgetLevel,
            String preferredTransport,
            String preferencesJson
    ) {
    }

    public record UpdateProfileRequest(
            @Size(max = 32) String gender,
            @Size(max = 32) String ageRange,
            @Size(max = 64) String travelStyle,
            @Size(max = 32) String defaultBudgetLevel,
            @Size(max = 64) String preferredTransport,
            String preferencesJson
    ) {
    }
}
```

- [ ] **Step 3: 创建业务异常**

```java
package com.aitour.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
```

- [ ] **Step 4: 创建统一异常处理**

```java
package com.aitour.infrastructure.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of(
                "code", ex.getCode(),
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", "VALIDATION_ERROR",
                "message", "请求参数不合法"
        ));
    }
}
```

## Task 4: JWT 服务和 Security 配置

**Files:**

- Create: `backend/src/main/java/com/aitour/infrastructure/security/JwtProperties.java`
- Create: `backend/src/main/java/com/aitour/infrastructure/security/JwtTokenService.java`
- Create: `backend/src/main/java/com/aitour/infrastructure/security/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/aitour/infrastructure/security/CurrentUser.java`
- Create: `backend/src/main/java/com/aitour/infrastructure/security/SecurityConfig.java`

- [ ] **Step 1: 创建 JWT 配置属性**

```java
package com.aitour.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String issuer,
        String secret,
        long accessTokenMinutes,
        long refreshTokenDays
) {
}
```

- [ ] **Step 2: 创建当前用户对象**

```java
package com.aitour.infrastructure.security;

public record CurrentUser(Long id, String username) {
}
```

- [ ] **Step 3: 创建 JWT 服务**

```java
package com.aitour.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {
    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.accessTokenMinutes() * 60)))
                .signWith(key)
                .compact();
    }

    public CurrentUser parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new CurrentUser(Long.valueOf(claims.getSubject()), claims.get("username", String.class));
    }
}
```

- [ ] **Step 4: 创建 JWT 过滤器**

```java
package com.aitour.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            CurrentUser user = jwtTokenService.parseAccessToken(token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, token, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 5: 创建 Security 配置**

```java
package com.aitour.infrastructure.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

## Task 5: AuthService 和认证接口

**Files:**

- Create: `backend/src/main/java/com/aitour/application/AuthService.java`
- Create: `backend/src/main/java/com/aitour/api/AuthController.java`
- Create: `backend/src/test/java/com/aitour/api/AuthControllerTest.java`

- [ ] **Step 1: 创建认证接口测试**

```java
package com.aitour.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRegisterAndLogin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"password123","nickname":"Alice"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("alice"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("alice"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd backend
mvn test -Dtest=AuthControllerTest
```

Expected:

```text
404
```

或编译失败提示 `AuthController` 不存在。

- [ ] **Step 3: 创建 `AuthService`**

```java
package com.aitour.application;

import com.aitour.api.dto.AuthDtos;
import com.aitour.domain.User;
import com.aitour.domain.UserStatus;
import com.aitour.infrastructure.exception.ApiException;
import com.aitour.infrastructure.persistence.UserMapper;
import com.aitour.infrastructure.security.JwtTokenService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        if (existing != null) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已存在");
        }
        Instant now = Instant.now();
        User user = new User();
        user.setId(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null ? request.username() : request.nickname());
        user.setStatus(UserStatus.ENABLED.name());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return toAuthResponse(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "用户名或密码错误");
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_DISABLED", "用户已被禁用");
        }
        return toAuthResponse(user);
    }

    private AuthDtos.AuthResponse toAuthResponse(User user) {
        String accessToken = jwtTokenService.createAccessToken(user.getId(), user.getUsername());
        String refreshToken = UUID.randomUUID().toString();
        return new AuthDtos.AuthResponse(accessToken, refreshToken, user.getId(), user.getUsername(), user.getNickname());
    }
}
```

- [ ] **Step 4: 创建 `AuthController`**

```java
package com.aitour.api;

import com.aitour.api.dto.AuthDtos;
import com.aitour.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run:

```bash
cd backend
mvn test -Dtest=AuthControllerTest
```

Expected:

```text
BUILD SUCCESS
```

## Task 6: 用户资料接口

**Files:**

- Create: `backend/src/main/java/com/aitour/application/UserProfileService.java`
- Create: `backend/src/main/java/com/aitour/api/UserController.java`
- Create: `backend/src/test/java/com/aitour/api/UserControllerTest.java`

- [ ] **Step 1: 创建用户资料接口测试**

```java
package com.aitour.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReadAndUpdateCurrentUserProfile() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bob","password":"password123","nickname":"Bob"}
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = body.replaceAll(".*\\"accessToken\\":\\"([^\\"]+)\\".*", "$1");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"));

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"travelStyle":"轻松","defaultBudgetLevel":"中等","preferredTransport":"地铁"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.travelStyle").value("轻松"));
    }
}
```

- [ ] **Step 2: 创建 `UserProfileService`**

```java
package com.aitour.application;

import com.aitour.api.dto.UserDtos;
import com.aitour.domain.User;
import com.aitour.domain.UserProfile;
import com.aitour.infrastructure.exception.ApiException;
import com.aitour.infrastructure.persistence.UserMapper;
import com.aitour.infrastructure.persistence.UserProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserProfileService {
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;

    public UserProfileService(UserMapper userMapper, UserProfileMapper userProfileMapper) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
    }

    public UserDtos.CurrentUserResponse currentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return new UserDtos.CurrentUserResponse(
                user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl(), user.getPhone(), user.getEmail()
        );
    }

    @Transactional
    public UserDtos.ProfileResponse updateProfile(Long userId, UserDtos.UpdateProfileRequest request) {
        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId)
        );
        Instant now = Instant.now();
        if (profile == null) {
            profile = new UserProfile();
            profile.setId(Math.abs(UUID.randomUUID().getMostSignificantBits()));
            profile.setUserId(userId);
            profile.setCreatedAt(now);
        }
        profile.setGender(request.gender());
        profile.setAgeRange(request.ageRange());
        profile.setTravelStyle(request.travelStyle());
        profile.setDefaultBudgetLevel(request.defaultBudgetLevel());
        profile.setPreferredTransport(request.preferredTransport());
        profile.setPreferencesJson(request.preferencesJson());
        profile.setUpdatedAt(now);
        if (profile.getCreatedAt().equals(now)) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }
        return new UserDtos.ProfileResponse(
                profile.getGender(), profile.getAgeRange(), profile.getTravelStyle(),
                profile.getDefaultBudgetLevel(), profile.getPreferredTransport(), profile.getPreferencesJson()
        );
    }
}
```

- [ ] **Step 3: 创建 `UserController`**

```java
package com.aitour.api;

import com.aitour.api.dto.UserDtos;
import com.aitour.application.UserProfileService;
import com.aitour.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public UserDtos.CurrentUserResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
        return userProfileService.currentUser(currentUser.id());
    }

    @PutMapping("/me/profile")
    public UserDtos.ProfileResponse updateProfile(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody UserDtos.UpdateProfileRequest request
    ) {
        return userProfileService.updateProfile(currentUser.id(), request);
    }
}
```

- [ ] **Step 4: 运行用户接口测试**

Run:

```bash
cd backend
mvn test -Dtest=UserControllerTest
```

Expected:

```text
BUILD SUCCESS
```

## Task 7: 完整验证和提交

**Files:**

- Modify: `README.md`

- [ ] **Step 1: 在 README 增加认证接口说明**

追加：

````markdown
## 认证接口

注册：

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123","nickname":"Alice"}'
```

登录：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}'
```

当前用户：

```bash
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <accessToken>"
```
````

- [ ] **Step 2: 运行完整后端测试**

Run:

```bash
cd backend
mvn test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 3: 提交认证模块**

Run:

```bash
git add backend README.md
git commit -m "feat: add authentication and user profile"
```

Expected:

```text
feat: add authentication and user profile
```

## 自检清单

- 注册、登录、当前用户、资料更新都有接口测试覆盖。
- 用户密码使用 BCrypt，不保存明文密码。
- 受保护接口通过 JWT 鉴权。
- Controller 不直接操作数据库。
- Redis 刷新令牌和黑名单可在后续安全增强中接入，当前 access token 主链路已完整。
