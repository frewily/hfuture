# HFuture 多环境配置说明

## 📁 配置文件结构

```
src/main/resources/
├── application.yml           # 主配置文件（共享配置）
├── application-dev.yml       # 开发环境配置
├── application-test.yml      # 测试环境配置
├── application-staging.yml   # 预发布环境配置
└── application-prod.yml      # 生产环境配置
```

## 🎯 环境说明

| 环境 | Profile | 说明 | 数据库 | Redis |
|------|---------|------|--------|-------|
| 开发环境 | dev | 本地开发 | localhost:3306/hfuture_dev | localhost:6379 |
| 测试环境 | test | 测试团队测试 | test-server:3306/hfuture_test | test-redis-server:6379 |
| 预发布环境 | staging | 生产镜像验证 | staging-db-read-replica:3306/hfuture_prod | staging-redis-replica:6379 |
| 生产环境 | prod | 真实用户使用 | prod-db-master:3306/hfuture_prod | prod-redis-master:6379 |

## 🚀 使用方法

### 方法一：IDEA 运行配置

1. 打开 IDEA 的 Run/Debug Configurations
2. 选择你的 Spring Boot 应用
3. 在 **VM options** 中添加：
   ```
   -Dspring.profiles.active=dev
   ```
4. 或者在 **Environment variables** 中添加：
   ```
   SPRING_PROFILES_ACTIVE=dev
   ```

### 方法二：命令行启动

```bash
# 开发环境
java -jar target/HFuture-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

# 测试环境
java -jar target/HFuture-0.0.1-SNAPSHOT.jar --spring.profiles.active=test

# 预发布环境
java -jar target/HFuture-0.0.1-SNAPSHOT.jar --spring.profiles.active=staging

# 生产环境
java -jar target/HFuture-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### 方法三：环境变量

```bash
# Linux/Mac
export SPRING_PROFILES_ACTIVE=prod
java -jar target/HFuture-0.0.1-SNAPSHOT.jar

# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE="prod"
java -jar target/HFuture-0.0.1-SNAPSHOT.jar
```

### 方法四：Maven 打包

```bash
# 打包时指定环境
mvn clean package -Dspring.profiles.active=prod
```

## 🔐 敏感信息管理

### 生产环境密码管理

**⚠️ 重要：不要在生产配置文件中硬编码密码！**

生产环境使用环境变量注入敏感信息：

```yaml
spring:
  datasource:
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  data:
    redis:
      password: ${REDIS_PASSWORD}
```

### 服务器部署时设置环境变量

#### Linux Systemd 服务

创建 `/etc/systemd/system/hfuture.service`:

```ini
[Unit]
Description=HFuture Application
After=network.target

[Service]
Type=simple
User=hfuture
WorkingDirectory=/opt/hfuture
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="DB_USER=hfuture_prod"
Environment="DB_PASSWORD=your_secure_password"
Environment="REDIS_PASSWORD=your_redis_password"
ExecStart=/usr/bin/java -jar /opt/hfuture/HFuture-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

#### Docker 部署

```bash
docker run -d \
  --name hfuture \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_USER=hfuture_prod \
  -e DB_PASSWORD=your_secure_password \
  -e REDIS_PASSWORD=your_redis_password \
  -p 8080:8080 \
  hfuture:latest
```

## 📊 配置差异对比

### 数据库连接池配置

| 环境 | 最大连接数 | 最小空闲连接 | 说明 |
|------|-----------|-------------|------|
| dev | 10 | 5 | 本地开发，资源有限 |
| test | 20 | 10 | 测试环境，中等规模 |
| staging | 30 | 15 | 预发布，接近生产 |
| prod | 50 | 20 | 生产环境，高并发 |

### Redis 连接池配置

| 环境 | 最大连接数 | 最大空闲连接 | 说明 |
|------|-----------|-------------|------|
| dev | 8 | 8 | 本地开发 |
| test | 16 | 8 | 测试环境 |
| staging | 24 | 12 | 预发布环境 |
| prod | 32 | 16 | 生产环境 |

### 日志级别配置

| 环境 | 应用日志 | Spring日志 | 说明 |
|------|---------|-----------|------|
| dev | DEBUG | DEBUG | 详细日志，便于调试 |
| test | INFO | INFO | 标准日志 |
| staging | WARN | WARN | 警告及以上 |
| prod | ERROR | ERROR | 仅错误日志 |

## 🔧 预发布环境注意事项

**⚠️ 预发布环境必须满足以下条件：**

1. **使用生产数据库的只读副本**
   - 避免污染生产数据
   - 测试真实数据量

2. **使用生产 Redis 的副本**
   - 避免影响生产缓存

3. **配置与生产一致**
   - 相同的连接池大小
   - 相同的超时设置
   - 相同的安全配置

## 🛡️ 安全建议

1. **数据库安全**
   - 生产数据库禁止公网访问
   - 使用 SSL 连接
   - 定期更换密码

2. **Redis 安全**
   - 启用密码认证
   - 生产环境启用 SSL
   - 禁用危险命令

3. **配置文件安全**
   - 不要将敏感信息提交到 Git
   - 使用环境变量或配置中心
   - 定期审计配置文件

## 📝 开发流程建议

1. **本地开发**：使用 `dev` 环境
2. **提交代码**：代码推送到测试环境，使用 `test` 环境
3. **测试验证**：测试团队在测试环境验证
4. **预发布验证**：部署到预发布环境，使用 `staging` 环境
5. **生产发布**：验证通过后，部署到生产环境，使用 `prod` 环境

## 🚨 常见问题

### Q1: 如何确认当前使用的是哪个环境？

启动应用后，查看日志：
```
The following 1 profile is active: "dev"
```

### Q2: 配置文件加载顺序是什么？

1. `application.yml` (基础配置)
2. `application-{profile}.yml` (环境配置，覆盖基础配置)
3. 命令行参数 (最高优先级)

### Q3: 如何在代码中获取当前环境？

```java
@Autowired
private Environment environment;

public String getCurrentProfile() {
    return environment.getActiveProfiles()[0];
}
```

### Q4: 如何为不同环境配置不同的日志文件？

在 `application-prod.yml` 中：
```yaml
logging:
  file:
    name: /var/log/hfuture/application.log
```

## 📚 参考资料

- [Spring Boot Profiles 官方文档](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles)
- [Spring Boot 外部化配置](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
