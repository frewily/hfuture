# HFuture - 合肥工业大学教务系统课表抓取 API

基于 Spring Boot 开发，模拟 CAS 统一认证 + SSO 鉴权，自动获取合肥工业大学 EAMS5 课表数据，并以 JSON API 形式对外提供。

## 技术栈

| 组件 | 版本 |
|------|------|
| Spring Boot | 4.0.5 |
| Java | 21 |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| MyBatis-Plus | 3.5.7 |
| OkHttp | 4.12.0 |
| Fastjson2 | 2.0.47 |
| Hutool | 5.8.26 |

## 快速开始

### 1. 环境准备

- JDK 21+、MySQL 8.0+、Redis 6.0+、Maven 3.8+

### 2. 数据库初始化

```bash
mysql -u root -p < src/main/resources/schema.sql
```

> 默认数据库名 `hfuture_db`，如需修改请同步更改 `application-dev.yml`。

### 3. 修改配置

编辑 `src/main/resources/application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hfuture_dev?...
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
      password:       # 无密码留空
```

### 4. 启动

```bash
mvn spring-boot:run
# 或
mvn clean package && java -jar target/HFuture-0.0.1-SNAPSHOT.jar
```

服务默认监听 `http://localhost:8080`。

---

## API 文档

所有接口返回统一格式：

```json
{ "code": 200, "msg": "...", "data": { ... } }
```

需要登录的接口在 `Authorization` 请求头中携带 `Bearer <token>`。

---

### 1. 获取验证码

**GET** `/api/v1/auth/captcha`

无需认证。

**响应**

```json
{
  "code": 200,
  "msg": "获取验证码成功",
  "data": {
    "loginSessionId": "a1b2c3d4...",
    "captchaImage": "data:image/jpeg;base64,/9j/..."
  }
}
```

> `loginSessionId` 有效期 5 分钟，登录时需要携带。

---

### 2. 登录

**POST** `/api/v1/auth/login`

无需认证。

**请求体**

```json
{
  "loginSessionId": "a1b2c3d4...",
  "studentId": "2025218716",
  "password": "your_password",
  "captcha": "1234"
}
```

**响应**

```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "userInfo": {
      "studentId": "2025218716",
      "name": "付诺",
      "major": "软件工程",
      "college": "软件学院",
      "avatarFrame": "beta_tester"
    }
  }
}
```

> Token 有效期 24 小时，教务 Session 有效期 3 小时。

---

### 3. 获取当前用户信息

**GET** `/api/v1/auth/me`

需要认证。无需请求参数。

**响应**

```json
{
  "code": 200,
  "msg": "获取用户信息成功",
  "data": {
    "studentId": "2025218716",
    "name": "付诺",
    "major": "软件工程",
    "college": "软件学院",
    "avatarFrame": "beta_tester"
  }
}
```

---

### 4. 获取当前课表

**GET** `/api/v1/schedule/current`

需要认证。无需请求参数，自动使用当前学期。

**响应**

```json
{
  "code": 200,
  "msg": "获取课表成功",
  "data": [
    {
      "courseName": "数据结构",
      "teacher": "张老师",
      "classroom": "翡翠湖校区 教A101(100)",
      "dayOfWeek": 1,
      "section": "1-2",
      "weekInfo": "1~18",
      "campusName": "翡翠湖校区"
    }
  ]
}
```

| 字段 | 说明 |
|------|------|
| `dayOfWeek` | 星期几，1=周一，7=周日 |
| `section` | 节次范围，如 `"3-4"` 表示第3-4节 |
| `weekInfo` | 上课周次，如 `"1~18"` |
| `campusName` | 校区名称 |

---

## 系统架构

```
客户端请求
    │
    ├─ GET  /api/v1/auth/captcha    → CasAuthService.initCasSession()
    │                                  → 返回验证码图片 + loginSessionId
    │
    ├─ POST /api/v1/auth/login      → CasAuthService.casLogin()        (CAS 登录，获取 TGC)
    │                                  → SsoAuthService.getTicket()     (申请 Ticket)
    │                                  → SsoAuthService.activateSession() (激活 EAMS SESSION)
    │                                  → CourseTableService.getDataId() (获取学生 dataId)
    │                                  → CourseTableService.getEnvironmentVariables() (bizTypeId/semesterId)
    │                                  → CourseTableService.fetchStudentInfoFromHome() (姓名/专业/学院)
    │                                  → 写入 Redis (hfut:session:{studentId}, 3h)
    │                                  → 写入 MySQL t_student (upsert)
    │                                  → 返回 JWT Token + userInfo
    │
    ├─ GET  /api/v1/auth/me         → 读 Redis → 返回 userInfo
    │
    └─ GET  /api/v1/schedule/current → 读 Redis → CourseTableService.getCourseTable()
                                        → EAMS get-data API → 解析课程数据 → 返回
```

## 数据库表结构

```sql
-- 学生信息（登录时自动 upsert）
t_student: id, student_no, name, data_id, biz_type_id, session_key, ...

-- 课程信息（预留，当前未写入）
t_course: id, lesson_id, course_code, course_name, teacher_name, semester_id, ...

-- 课表安排（预留，当前未写入）
t_schedule: id, student_id, course_id, week_day, start_unit, end_unit,
            room_name, campus_name, week_info, semester_id, ...
```

## 注意事项

- **密码加密**：CAS 登录使用 AES ECB 加密，密钥从 `LOGIN_FLAVORING` Cookie 动态获取。
- **验证码**：需要用户手动识别，`loginSessionId` 5 分钟内有效。
- **Session 有效期**：教务 SESSION 3 小时后失效，需重新登录。
- **semesterId**：从课表页 `<option selected>` 自动解析，无需手动配置。
- **本项目仅供学习研究使用，请勿用于商业或违规用途。**
