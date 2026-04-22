# HFuture Mock 模式测试指南

## 🎯 为什么需要 Mock 模式？

在测试环境中使用真实的 CAS 和教务系统存在以下风险：

| 风险 | 说明 | 影响 |
|------|------|------|
| **数据污染** | 测试数据可能影响真实系统 | 严重 |
| **账号安全** | 测试账号可能被锁定或泄露 | 高 |
| **性能影响** | 测试请求可能影响真实用户体验 | 中 |
| **合规问题** | 可能违反学校安全政策 | 高 |

## 📋 Mock 模式功能

Mock 模式提供以下模拟功能：

### 1. CAS 认证模拟
- ✅ 模拟 CAS 会话初始化
- ✅ 模拟验证码获取
- ✅ 模拟 CAS 登录
- ✅ 模拟 TGC 生成

### 2. 教务系统模拟
- ✅ 模拟 SSO 鉴权
- ✅ 模拟会话激活
- ✅ 模拟课表数据获取

## 🚀 使用方法

### 方法一：使用 Mock 配置文件

1. **启动 Mock 模式**：
   ```bash
   java -jar target/HFuture-0.0.1-SNAPSHOT.jar --spring.profiles.active=mock
   ```

2. **配置说明**：
   ```yaml
   hfut:
     mock:
       enabled: true           # 启用 Mock 模式
       captcha: "1234"         # 固定验证码
       username: "2025218716"  # 测试用户名
       password: "Tghr2994508531"  # 测试密码
   ```

### 方法二：在测试代码中使用

```java
@SpringBootTest
@ActiveProfiles("mock")
class HFutureApplicationTests {
    
    @Autowired
    private CasAuthService casAuthService;
    
    @Test
    void testMockLogin() throws IOException {
        // 初始化会话
        SessionInfo sessionInfo = casAuthService.initCasSession();
        
        // 获取验证码（Mock 模式下返回固定验证码）
        byte[] captchaImage = casAuthService.getCaptchaImage(sessionInfo);
        
        // 登录（Mock 模式下使用配置的验证码）
        boolean success = casAuthService.casLogin(
            sessionInfo, 
            "2025218716", 
            "Tghr2994508531", 
            "1234"  // Mock 模式下的固定验证码
        );
        
        assertTrue(success);
    }
}
```

## 📊 Mock 模式 vs 真实模式对比

| 功能 | Mock 模式 | 真实模式 |
|------|----------|---------|
| **验证码** | 固定文本 "1234" | 真实图片验证码 |
| **登录** | 总是成功（验证码正确时） | 需要真实账号密码 |
| **TGC** | 生成的模拟 TGC | 真实的 TGC |
| **课表数据** | 模拟数据 | 真实课表数据 |
| **网络请求** | 无 | 需要访问合工大服务器 |

## 🔧 Mock 模式配置详解

### application-mock.yml 配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hfuture_test
    username: root
    password: root
  
  data:
    redis:
      host: localhost
      port: 6379
      database: 1

hfut:
  mock:
    enabled: true
    captcha: "1234"
    username: "2025218716"
    password: "Tghr2994508531"
```

### 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `hfut.mock.enabled` | boolean | false | 是否启用 Mock 模式 |
| `hfut.mock.captcha` | String | "1234" | Mock 验证码 |
| `hfut.mock.username` | String | "test_user" | Mock 用户名 |
| `hfut.mock.password` | String | "test_password" | Mock 密码 |

## 🧪 测试场景

### 场景一：单元测试

```java
@SpringBootTest
@ActiveProfiles("mock")
class CourseTableServiceTest {
    
    @Autowired
    private CourseTableFacadeService facadeService;
    
    @Test
    void testGetCourseTable() throws IOException {
        // 使用 Mock 模式测试课表获取
        List<CourseTableResponse> courses = facadeService.getCourseTable(
            "2025218716",
            "Tghr2994508531",
            "1234"
        );
        
        assertNotNull(courses);
    }
}
```

### 场景二：集成测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mock")
class CourseTableControllerTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testLogin() {
        // 测试登录接口
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/course/login",
            new LoginRequest("2025218716", "Tghr2994508531", "1234"),
            String.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
```

### 场景三：API 测试

使用 Apifox 或 Postman 测试：

1. **启动 Mock 模式**：
   ```bash
   java -jar target/HFuture-0.0.1-SNAPSHOT.jar --spring.profiles.active=mock
   ```

2. **获取验证码**：
   ```
   GET http://localhost:8080/api/course/captcha
   ```
   响应：文本 "Mock Captcha: 1234"

3. **登录**：
   ```
   POST http://localhost:8080/api/course/login
   Body: {"username": "2025218716", "password": "Tghr2994508531", "captcha": "1234"}
   ```
   响应：登录成功

## 📝 Mock 数据示例

### Mock 验证码
```
Mock Captcha: 1234
```

### Mock TGC
```
MOCK_TGC_1713692400000
```

### Mock 课表数据
```json
[
  {
    "lessonId": 123456,
    "courseCode": "MOCK_COURSE_001",
    "courseName": "数据结构（Mock）",
    "courseTypeName": "通识必修课",
    "teacherName": "张老师（Mock）",
    "actualPeriods": 48,
    "suggestScheduleWeekInfo": "1~16"
  }
]
```

## ⚠️ 注意事项

### 1. Mock 模式仅用于测试
- ❌ 不要在生产环境启用 Mock 模式
- ❌ 不要将 Mock 模式用于真实业务
- ✅ 仅在开发和测试环境使用

### 2. 数据隔离
- Mock 模式使用独立的数据库（hfuture_test）
- Mock 模式使用独立的 Redis 数据库（database: 1）
- 确保测试数据不会污染生产环境

### 3. 安全考虑
- Mock 配置文件中的密码仅用于测试
- 不要在 Mock 配置中使用真实的生产密码
- 定期更换测试环境的密码

## 🔄 切换到真实模式

当需要测试真实系统时：

### 方法一：修改配置文件
```yaml
hfut:
  mock:
    enabled: false  # 禁用 Mock 模式
```

### 方法二：使用不同的 Profile
```bash
# 使用开发环境（真实系统）
java -jar target/HFuture-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

# 使用 Mock 模式
java -jar target/HFuture-0.0.1-SNAPSHOT.jar --spring.profiles.active=mock
```

## 📚 相关文档

- [多环境配置说明](ENVIRONMENT_CONFIG.md)
- [API 接口文档](HFuture%20-%20教务系统课表抓取核心%20API%20文档.md)

## 🎯 最佳实践

1. **开发阶段**：使用 Mock 模式快速迭代
2. **单元测试**：使用 Mock 模式隔离外部依赖
3. **集成测试**：使用 Mock 模式测试完整流程
4. **预发布测试**：使用真实系统进行最终验证
5. **生产环境**：禁用 Mock 模式

## 🚨 故障排查

### 问题1：Mock 模式未生效
**检查**：
- 确认 `spring.profiles.active=mock`
- 确认 `hfut.mock.enabled=true`

### 问题2：验证码错误
**检查**：
- Mock 模式下验证码固定为 "1234"
- 检查配置文件中的 `hfut.mock.captcha`

### 问题3：登录失败
**检查**：
- Mock 模式下任意用户名密码都可以登录
- 确保验证码正确

## 📞 技术支持

如有问题，请查看：
- 项目文档
- 日志文件
- 联系开发团队
