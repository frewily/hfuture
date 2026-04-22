# HFuture 项目说明文档

## 1. 项目简介

HFuture 是一个基于 Spring Boot 开发的合肥工业大学教务系统课表抓取 API，通过模拟 CAS 统一认证和 SSO 单点登录，自动获取 EAMS5 系统的课表数据，并以 JSON API 形式对外提供。

**核心功能**：
- CAS 统一认证登录（支持验证码）
- SSO 单点登录和 Session 激活
- 课表数据抓取和解析
- JWT Token 认证
- Redis Session 管理
- 多环境配置支持
- Mock 服务用于测试

## 2. 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.0.5 | 应用框架 |
| Java | 21 | 开发语言 |
| MySQL | 8.0+ | 持久化存储 |
| Redis | 6.0+ | 会话管理 |
| MyBatis-Plus | 3.5.7 | ORM 框架 |
| OkHttp | 4.12.0 | HTTP 客户端 |
| Fastjson2 | 2.0.47 | JSON 解析 |
| Hutool | 5.8.26 | 工具库 |

## 3. 环境要求

### 3.1 系统要求
- Windows/Linux/MacOS
- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+

### 3.2 网络要求
- 能访问合肥工业大学 CAS 系统 (`http://cas.hfut.edu.cn`)
- 能访问合肥工业大学教务系统 (`http://jxglstu.hfut.edu.cn`)

## 4. 安装部署

### 4.1 克隆项目

```bash
git clone https://github.com/frewily/hfuture.git
cd hfuture
```

### 4.2 数据库初始化

执行 SQL 脚本创建数据库和表结构：

```bash
mysql -u root -p < src/main/resources/schema.sql
```

**默认数据库**：`hfuture_dev`
**默认用户**：`root` / `root`

### 4.3 配置文件设置

#### 方法一：环境变量（推荐）

```bash
# Linux/Mac
export DB_USERNAME=root
export DB_PASSWORD=root
export REDIS_HOST=localhost
export JWT_SECRET=your_secret_key

# Windows (PowerShell)
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "root"
$env:REDIS_HOST = "localhost"
$env:JWT_SECRET = "your_secret_key"
```

#### 方法二：直接修改配置文件

编辑 `src/main/resources/application-dev.yml`：

```yaml
spring:
  datasource:
    username: root
    password: your_password
  data:
    redis:
      host: localhost
```

### 4.4 启动服务

```bash
# 1. 启动 Redis
redis-server

# 2. 启动应用
mvn spring-boot:run
```

服务默认运行在 `http://localhost:8080`。

## 5. API 文档

### 5.1 统一响应格式

所有接口返回格式：

```json
{ "code": 200, "msg": "成功", "data": { ... } }
```

| 状态码 | 含义 |
|--------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | Token 过期或无效 |
| 403 | 验证码错误 |
| 500 | 服务器内部错误 |

### 5.2 接口列表

#### 1. 获取验证码
- **方法**：GET
- **路径**：`/api/v1/auth/captcha`
- **认证**：无
- **响应**：
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

#### 2. 登录
- **方法**：POST
- **路径**：`/api/v1/auth/login`
- **认证**：无
- **请求体**：
  ```json
  {
    "loginSessionId": "a1b2c3d4...",
    "studentId": "2025218716",
    "password": "your_password",
    "captcha": "1234"
  }
  ```
- **响应**：
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

#### 3. 获取当前用户信息
- **方法**：GET
- **路径**：`/api/v1/auth/me`
- **认证**：需要（`Authorization: Bearer <token>`）
- **响应**：
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

#### 4. 获取当前课表
- **方法**：GET
- **路径**：`/api/v1/schedule/current`
- **认证**：需要（`Authorization: Bearer <token>`）
- **响应**：
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

## 6. 数据库配置

### 6.1 数据库表结构

```sql
-- 学生信息表
CREATE TABLE t_student (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_no VARCHAR(20) UNIQUE COMMENT '学号',
    name VARCHAR(50) COMMENT '姓名',
    major VARCHAR(100) COMMENT '专业',
    college VARCHAR(100) COMMENT '学院',
    data_id BIGINT COMMENT '教务系统dataId',
    biz_type_id INT COMMENT '业务类型ID',
    semester_id INT COMMENT '学期ID',
    session_key VARCHAR(255) COMMENT 'Redis会话key',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 课程信息表（预留）
CREATE TABLE t_course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lesson_id BIGINT COMMENT '课程ID',
    course_code VARCHAR(50) COMMENT '课程代码',
    course_name VARCHAR(100) COMMENT '课程名称',
    teacher_name VARCHAR(50) COMMENT '教师姓名',
    semester_id INT COMMENT '学期ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 课表安排表（预留）
CREATE TABLE t_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT COMMENT '学生ID',
    course_id BIGINT COMMENT '课程ID',
    week_day INT COMMENT '星期几',
    start_unit INT COMMENT '开始节次',
    end_unit INT COMMENT '结束节次',
    room_name VARCHAR(100) COMMENT '教室',
    campus_name VARCHAR(50) COMMENT '校区',
    week_info VARCHAR(50) COMMENT '周次',
    semester_id INT COMMENT '学期ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 6.2 数据库连接配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 地址 | localhost:3306 | MySQL 服务器地址 |
| 数据库 | hfuture_dev | 数据库名称 |
| 用户名 | root | 数据库用户名 |
| 密码 | root | 数据库密码 |
| 连接池 | HikariCP | 高性能连接池 |

## 7. Redis 配置

### 7.1 Redis 存储内容

| Key 格式 | 类型 | 过期时间 | 说明 |
|----------|------|----------|------|
| `hfut:session:{studentId}` | String (JSON) | 3小时 | 会话信息 |
| `hfut:captcha:{loginSessionId}` | String | 5分钟 | 验证码 |

### 7.2 Redis 连接配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 地址 | localhost:6379 | Redis 服务器地址 |
| 数据库 | 0 | Redis 数据库编号 |
| 密码 | 无 | Redis 密码 |
| 超时 | 3000ms | 连接超时时间 |

## 8. 前后端联调

### 8.1 本地开发

- **后端**：`http://localhost:8080`
- **前端**：`http://localhost:3000`（React/Vue 等）
- **跨域**：已配置支持 `http://localhost:3000`

### 8.2 前端调用示例

```javascript
// 1. 获取验证码
async function getCaptcha() {
  const response = await fetch('http://localhost:8080/api/v1/auth/captcha');
  const data = await response.json();
  return data.data;
}

// 2. 登录
async function login(sessionId, studentId, password, captcha) {
  const response = await fetch('http://localhost:8080/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      loginSessionId: sessionId,
      studentId,
      password,
      captcha
    })
  });
  const data = await response.json();
  localStorage.setItem('token', data.data.token);
  return data.data.userInfo;
}

// 3. 获取课表
async function getSchedule() {
  const token = localStorage.getItem('token');
  const response = await fetch('http://localhost:8080/api/v1/schedule/current', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return await response.json();
}
```

## 9. 多环境配置

| 环境 | 配置文件 | 说明 |
|------|----------|------|
| 开发 | application-dev.yml | 本地开发环境 |
| 测试 | application-test.yml | 测试环境 |
| 预发 | application-staging.yml | 预发布环境 |
| 生产 | application-prod.yml | 生产环境 |
| Mock | application-mock.yml | 模拟环境（无需真实 CAS） |

**切换环境**：修改 `application.yml` 中的 `spring.profiles.active`

## 10. 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 数据库连接失败 | 密码错误 | 检查 `DB_PASSWORD` 环境变量 |
| Redis 连接失败 | Redis 未启动 | 执行 `redis-server` |
| 验证码获取失败 | CAS 网络问题 | 检查网络连接 |
| 登录失败 | 密码错误 | 检查学号密码 |
| 课表为空 | 学期未开始 | 检查 semesterId 是否正确 |
| Token 过期 | 超过 24 小时 | 重新登录 |
| Session 过期 | 超过 3 小时 | 重新登录 |

## 11. 注意事项

1. **安全**：JWT Secret 建议使用复杂的随机字符串
2. **性能**：Redis 用于存储会话，建议配置足够的内存
3. **网络**：确保能访问合肥工业大学内部系统
4. **权限**：数据库用户需要有足够的权限
5. **合规**：本项目仅供学习研究使用，请勿用于商业或违规用途

## 12. 项目结构

```
HFuture/
├── src/
│   ├── main/
│   │   ├── java/top/hfuture/
│   │   │   ├── business/         # 业务逻辑
│   │   │   │   ├── controller/   # 控制器
│   │   │   │   ├── service/      # 服务层
│   │   │   │   ├── model/        # 模型
│   │   │   │   └── dto/           # 数据传输对象
│   │   │   ├── common/           # 通用组件
│   │   │   └── config/           # 配置类
│   │   └── resources/            # 资源文件
│   │       ├── application-*.yml  # 配置文件
│   │       └── schema.sql        # 数据库脚本
│   └── test/                      # 测试代码
├── .gitignore                     # Git 忽略文件
├── pom.xml                        # Maven 配置
└── README.md                      # 项目说明
```

## 13. 开发命令

| 命令 | 说明 |
|------|------|
| `mvn spring-boot:run` | 启动开发服务器 |
| `mvn clean package` | 构建项目 |
| `java -jar target/HFuture-0.0.1-SNAPSHOT.jar` | 运行打包后的应用 |
| `mvn test` | 运行测试 |

## 14. 联系方式

- **项目地址**：https://github.com/frewily/hfuture
- **维护者**：frewily
- **邮箱**：frewily@gmail.com

---

**© 2026 HFuture 项目组**
