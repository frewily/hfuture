# HFuture 项目交接说明

> 本文件面向接手后端的队友，帮助快速了解项目现状、运行方式与注意事项。

---

## 一、项目背景

本项目是一个基于 **Spring Boot 4** 的 REST API 服务，目标是通过模拟 CAS 统一认证 + SSO 登录，自动抓取合肥工业大学 EAMS5 教务系统中的个人课表，以 JSON 格式对外暴露。

**典型使用场景**：前端/小程序调用本服务，展示当前学期课表，无需用户手动登录教务系统网页。

---

## 二、技术栈一览

| 层次 | 技术 |
|------|------|
| 框架 | Spring Boot 4.0.5 / Java 21 |
| 数据库 | MySQL 8.0 + MyBatis-Plus 3.5.7 |
| 缓存/会话 | Redis 6.0 |
| HTTP 客户端 | OkHttp 4.12.0 |
| HTML 解析 | Jsoup 1.17.2 |
| 认证 | JWT（JJWT 0.12.5）|
| 构建 | Maven（项目自带 mvnw，无需单独安装）|

---

## 三、项目结构

```
src/main/java/top/hfuture/
├── HFutureApplication.java          # 启动入口
├── business/
│   ├── controller/
│   │   ├── AuthController.java      # /api/v1/auth/* 认证接口
│   │   └── ScheduleController.java  # /api/v1/schedule/* 课表接口
│   ├── service/
│   │   ├── CasAuthService.java      # CAS 登录核心（验证码、AES 加密、登录）
│   │   ├── SsoAuthService.java      # SSO Ticket 申请 + EAMS Session 激活
│   │   ├── CourseTableService.java  # 课表数据抓取与解析
│   │   ├── CourseTableFacadeService.java  # 对外统一入口（编排以上服务）
│   │   ├── SessionManagerService.java    # Redis 会话管理
│   │   └── MockAuthService.java     # Mock 模式（测试用，跳过真实 CAS）
│   ├── entity/                      # 数据库实体（Student, Course, Schedule）
│   ├── mapper/                      # MyBatis-Plus Mapper
│   ├── dto/                         # 请求/响应 DTO
│   └── model/                       # 内部数据模型（SessionInfo 等）
├── common/
│   ├── dto/Result.java              # 统一响应包装 {code, msg, data}
│   ├── entity/BaseEntity.java       # 通用字段（id, create_time 等）
│   ├── exception/GlobalExceptionHandler.java
│   └── util/JwtUtil.java
└── config/                          # 各种配置类（JWT、OkHttp、MyBatis、拦截器等）

src/main/resources/
├── application.yml                  # 基础配置（激活 profile、JWT 密钥等）
├── application-dev.yml              # 开发环境（本地 MySQL/Redis）
├── application-mock.yml             # Mock 环境（测试用，无需真实学号）
├── application-prod.yml             # 生产环境（改用环境变量注入凭据）
└── schema.sql                       # 建表 SQL（首次运行前执行一次）
```

---

## 四、快速启动（开发环境）

### 1. 前置条件

- JDK 21+
- MySQL 8.0+（本地或 Docker）
- Redis 6.0+（本地或 Docker）
- Maven 3.8+（或直接用项目自带的 `./mvnw`）

### 2. 初始化数据库

```bash
mysql -u root -p < src/main/resources/schema.sql
```

数据库名默认为 `hfuture_dev`，如需修改，同步改 `application-dev.yml`。

### 3. 修改本地配置

编辑 `src/main/resources/application-dev.yml`，填入你本地的 MySQL 和 Redis 连接信息：

```yaml
spring:
  datasource:
    username: root
    password: 你的密码
  data:
    redis:
      host: localhost
      port: 6379
      password:   # 无密码留空
```

> **注意**：不要把真实密码提交到 git！`.env` 和 `application-local.yml` 已在 `.gitignore` 中排除。

### 4. 启动

```bash
./mvnw spring-boot:run
# 或
./mvnw clean package && java -jar target/HFuture-0.0.1-SNAPSHOT.jar
```

服务监听 `http://localhost:8080`。

### 5. 用 Mock 模式测试（推荐新接手时先用这个）

Mock 模式下无需真实学号，直接返回固定数据，可验证接口是否通：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mock
```

> 详见项目根目录的 `MOCK_GUIDE.md`。

---

## 五、API 接口速览

| 方法 | 路径 | 是否需要 Token | 说明 |
|------|------|:-----------:|------|
| GET | `/api/v1/auth/captcha` | 否 | 获取验证码图片（Base64）+ loginSessionId |
| POST | `/api/v1/auth/login` | 否 | CAS 登录，返回 JWT Token |
| GET | `/api/v1/auth/me` | **是** | 获取当前用户信息 |
| GET | `/api/v1/schedule/current` | **是** | 获取当前学期课表 |

认证接口在请求头加：`Authorization: Bearer <token>`

完整接口文档见根目录的 `HFuture API 接口文档 (v1.1).md`。

---

## 六、核心流程说明

登录流程（最复杂，务必理解）：

```
1. 前端 GET /captcha → 拿到 loginSessionId + 验证码图片
2. 用户输入学号、密码、验证码 → 前端 POST /login
3. 后端：
   a. 用 loginSessionId 从 Redis 取 CAS Session（含 Cookie）
   b. 用 AES ECB 加密密码（密钥来自 LOGIN_FLAVORING Cookie）
   c. 携带加密密码 + 验证码请求 CAS 登录，拿到 TGC（Ticket Granting Cookie）
   d. 用 TGC 向 SSO 申请 Ticket
   e. 用 Ticket 激活 EAMS5 的 SESSION
   f. 从 EAMS 获取学生 dataId（课表抓取必需）、bizTypeId、semesterId、姓名/专业/学院
   g. 将 SessionInfo（含所有 Cookie）写入 Redis（3 小时 TTL）
   h. 将学生基本信息 upsert 到 MySQL t_student
   i. 生成 JWT Token 返回给前端
4. 前端后续请求带 JWT → 后端从 Redis 恢复 Session → 代理请求 EAMS
```

**关键细节**：
- AES 密钥每次会话不同，从 CAS 返回的 `LOGIN_FLAVORING` Cookie 动态读取
- semesterId 从 EAMS 课表页的 `<option selected>` 自动解析，无需手动配置
- EAMS SESSION 3 小时失效，需重新登录

---

## 七、数据库表

```sql
t_student   -- 学生信息，登录时自动 upsert
t_course    -- 课程信息（预留，目前不写入）
t_schedule  -- 课表安排（预留，目前不写入）
```

完整建表语句见 `src/main/resources/schema.sql`。

---

## 八、现存问题 / 后续工作

- [ ] `t_course` / `t_schedule` 表预留但未落库，课表数据目前只在接口返回，不持久化
- [ ] 无单元测试，建议后续补充关键 Service 的 Mock 测试
- [ ] 验证码目前需要人工识别，未接 OCR
- [ ] EAMS SESSION 3 小时失效后前端会收到错误，需要前端引导重新登录
- [ ] 生产环境部署请使用环境变量注入数据库/Redis 密码，不要明文写在配置文件里

---

## 九、参考文档

| 文件 | 内容 |
|------|------|
| `README.md` | 快速启动 + 接口示例 |
| `PROJECT_GUIDE.md` | 详细架构说明 |
| `ENVIRONMENT_CONFIG.md` | 多环境配置说明（dev/test/staging/prod/mock）|
| `MOCK_GUIDE.md` | Mock 模式使用说明 |
| `HFuture API 接口文档 (v1.1).md` | 完整接口文档 |
| `src/main/resources/schema.sql` | 数据库建表语句 |

---

## 十、本地清理说明

以下文件属于开发过程中的临时产物，已在 `.gitignore` 中排除，不会提交到仓库：

- `captcha.jpg` — 调试时保存的验证码图片
- `cas_login.html` — 调试时保存的 CAS 页面
- `ANALYSIS_REPORT.txt` — 本地分析输出
- `.env` — 本地数据库密码（**绝对不要提交**）

如果你本地出现这些文件，直接删除即可，不影响项目运行。

---

> 如有疑问，可参考代码中的注释，或联系原作者查看 git log 中的提交记录。
