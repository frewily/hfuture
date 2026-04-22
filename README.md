# HFuture - 合肥工业大学教务系统课表抓取系统

## 项目简介

本项目基于 Spring Boot 4.0.5 + Java 21 开发，实现了合肥工业大学教务系统（EAMS5）课表数据的自动抓取功能。系统通过模拟 CAS 统一认证登录，穿透 SSO 鉴权，最终获取学生的完整课表数据。

## 技术栈

- **框架**: Spring Boot 4.0.5
- **Java版本**: Java 21
- **数据库**: MySQL 8.0+
- **缓存**: Redis
- **ORM**: MyBatis-Plus 3.5.7
- **HTTP客户端**: OkHttp 4.12.0
- **工具库**: Hutool 5.8.26, Jsoup 1.17.2, Fastjson2 2.0.47

## 核心功能

### 1. CAS 统一认证（阶段一）
- 初始化 CAS 会话，获取 Cookie 和 execution 参数
- 获取图片验证码和风控 Cookie
- 提交账号密码登录，获取 TGC 凭证

### 2. SSO 鉴权与会话激活（阶段二）
- 申请教务系统专属 Ticket
- 换取并激活教务 SESSION

### 3. 课表数据获取（阶段三）
- 提取学生内部 ID (dataId)
- 获取全局环境变量 (bizTypeId & semesterId)
- 获取本学期课程汇总
- 获取最终完整课表

## 项目结构

```
src/main/java/top/hfuture/
├── common/
│   ├── dto/              # 通用DTO
│   ├── entity/           # 基础实体类
│   └── exception/        # 异常处理
├── config/               # 配置类
├── business/
│   ├── controller/       # 控制器
│   ├── dto/              # 业务DTO
│   ├── entity/           # 业务实体
│   ├── mapper/           # MyBatis Mapper
│   ├── model/            # 业务模型
│   └── service/          # 业务服务
└── HFutureApplication.java
```

## 快速开始

### 1. 环境准备

确保已安装以下环境：
- JDK 21+
- MySQL 8.0+
- Redis 6.0+
- Maven 3.8+

### 2. 数据库初始化

```bash
# 创建数据库并执行初始化脚本
mysql -u root -p < src/main/resources/schema.sql
```

### 3. 配置文件修改

修改 `src/main/resources/application.yaml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hfuture?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: your_username
    password: your_password
  
  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password
```

### 4. 启动项目

```bash
# 编译项目
mvn clean package

# 运行项目
java -jar target/HFuture-0.0.1-SNAPSHOT.jar
```

## API 接口

### 1. 登录接口

**POST** `/api/course/login`

请求体：
```json
{
  "username": "学号",
  "password": "密码",
  "captcha": "验证码"
}
```

响应：
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "studentNo": "2025xxxxxx",
    "tgc": "TGC-xxx",
    "session": "xxx",
    "dataId": 123456,
    "bizTypeId": 2,
    "semesterId": 334
  }
}
```

### 2. 获取课表接口

**POST** `/api/course/table`

请求体：
```json
{
  "username": "学号",
  "password": "密码",
  "captcha": "验证码"
}
```

响应：
```json
{
  "code": 200,
  "message": "获取课表成功",
  "data": [
    {
      "lessonId": 123456,
      "courseCode": "0000001X--001",
      "courseName": "数据结构",
      "courseTypeName": "通识必修课",
      "teacherName": "张老师",
      "actualPeriods": 48,
      "suggestScheduleWeekInfo": "1~16",
      "scheduleDetails": [
        {
          "weekDay": 1,
          "startUnit": 1,
          "endUnit": 2,
          "roomName": "教学楼A101",
          "campusName": "翡翠湖校区",
          "weekInfo": "1~16"
        }
      ]
    }
  ]
}
```

## 注意事项

### 1. 验证码处理
目前系统需要手动输入验证码。后续可以集成 OCR 识别或第三方验证码识别服务。

### 2. 密码加密
系统使用 AES ECB 算法对密码进行加密，密钥为 `hfut1234567890ab`。如需修改，请更新 [CasAuthService.java](src/main/java/top/hfuture/business/service/CasAuthService.java) 中的 `AES_KEY` 常量。

### 3. 会话管理
- 教务 SESSION 有效期为 3 小时
- 系统会自动将会话信息存储到 Redis 中
- 如果会话失效，系统会自动重新登录

### 4. Cookie 维护
系统使用 OkHttp 的 Cookie Jar 机制自动维护 Cookie，确保会话的连续性。

## 常见问题

### 1. 登录失败
- 检查用户名、密码是否正确
- 检查验证码是否正确
- 检查网络连接是否正常

### 2. 获取课表失败
- 检查 SESSION 是否有效
- 检查 Redis 连接是否正常
- 查看日志排查具体错误

### 3. 数据库连接失败
- 检查 MySQL 服务是否启动
- 检查数据库配置是否正确
- 检查数据库用户权限

## 开发计划

- [ ] 集成验证码自动识别
- [ ] 添加课表数据持久化功能
- [ ] 实现课表数据定时同步
- [ ] 添加课表导出功能（Excel、PDF）
- [ ] 开发前端界面

## 许可证

本项目仅供学习和研究使用，请勿用于商业用途。

## 联系方式

如有问题或建议，请提交 Issue 或 Pull Request。
