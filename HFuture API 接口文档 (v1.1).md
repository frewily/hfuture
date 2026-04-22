# HFuture API 接口文档 (v1.1)

## 全局规范说明

### 1. 认证方式 (Authentication)

除登录相关接口外，所有业务接口均需在 HTTP 请求头 (Header) 中携带 JWT Token 进行身份验证。

- **Header Key:** `Authorization`
- **Header Value:** `Bearer <Your_Token>`（注意 Bearer 和 Token 之间有一个空格）

### 2. 全局错误码及含义 (Error Codes)

系统采用统一的 HTTP 状态码 200 进行响应，具体的业务错误通过响应体中的 `code` 字段进行标识。

| **错误码 (code)** | **含义说明**            | **处理建议**                                |
| ----------------- | ----------------------- | ------------------------------------------- |
| `200`             | 请求成功                | 正常处理 `data` 数据                        |
| `400`             | 请求参数错误            | 检查请求体或 URL 参数是否必填、格式是否正确 |
| `401`             | 认证失败或 Token 过期   | 提示用户“登录已过期”或强制跳转至登录页面    |
| `403`             | 图形验证码错误          | 提示用户“验证码错误，请点击刷新”            |
| `404`             | 资源不存在              | 提示用户该文件或记录已被删除                |
| `500`             | 服务器内部/教务抓取异常 | 提示“教务系统响应超时或系统开小差”          |

------

## 接口列表

### 一、 用户登录 (CAS 统一代理认证)

由于工大 CAS 统一认证可能触发图形验证码，登录流程拆分为**获取验证码**与**执行登录**两步。

#### 1.1 获取登录验证码 (预登录)

- **接口基本信息**
  - **接口名称：** 获取 CAS 图形验证码与会话 ID
  - **请求路径：** `/api/v1/auth/captcha`
  - **请求方法：** `GET`
  - **认证要求：** 无
- **响应数据**
  - **说明：** 返回 Base64 格式的图片，以及用于绑定此次 CAS 会话的临时 ID。前端需保存该 ID 用于下一步登录。

JSON

```json
{
  "code": 200,
  "msg": "获取验证码成功",
  "data": {
    "loginSessionId": "uuid-8f92-4b2a-91c8-abcdef123456",
    "captchaImage": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD..." 
  }
}
```

#### 1.2 执行登录与换取 Token

- **接口基本信息**
  - **接口名称：** 提交校验并获取业务 Token
  - **请求路径：** `/api/v1/auth/login`
  - **请求方法：** `POST`
  - **认证要求：** 无
- **请求参数 (Body - application/json)**

| **参数名**       | **类型** | **是否必填** | **说明**                                                     | **示例**              |
| ---------------- | -------- | ------------ | ------------------------------------------------------------ | --------------------- |
| `loginSessionId` | String   | 是           | 1.1 接口返回的临时会话 ID（后端凭此去 Redis 找对应的 CAS 状态） | `"uuid-8f92-4b2a..."` |
| `studentId`      | String   | 是           | 教务系统学号                                                 | `"2025123456"`        |
| `password`       | String   | 是           | 明文密码（由后端统一进行 AES 加密处理）                      | `"HFUT@2025"`         |
| `captcha`        | String   | 否           | 用户输入的图形验证码（视学校网关风控情况，若无验证码可留空） | `"abcd"`              |

- **响应数据 (200 OK 成功示例):**
  - **说明：** 登录成功后，后端将教务的 `SESSION` 截留并缓存在服务器，仅给前端下发 HFuture 专属的 JWT。同时在 `userInfo` 中返回从信息门户拉取的人员基本信息。

JSON

```JSON
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdHVkZW5...",
    "userInfo": {
      "studentId": "2025123456",
      "name": "李同学",
      "major": "计算机科学与技术",
      "college": "计算机与信息学院",
      "avatarFrame": "beta_tester" 
    }
  }
}
```

- **调用示例 (微信小程序原生语法)**

JavaScript

```JavaScript
wx.request({
  url: 'https://api.yourdomain.com/api/v1/auth/login',
  method: 'POST',
  data: {
    loginSessionId: this.data.currentSessionId,
    studentId: '2025123456',
    password: 'your_password',
    captcha: 'abcd'
  },
  success(res) {
    if(res.data.code === 200) {
      wx.setStorageSync('token', res.data.data.token);
      wx.setStorageSync('userInfo', res.data.data.userInfo);
      console.log('欢迎登入 HFuture!');
    } else {
      // 若返回 403，需重新请求 1.1 接口刷新验证码
      wx.showToast({ title: res.data.msg, icon: 'none' });
    }
  }
});
```

------

### 二、 获取当前课表

- **接口基本信息**

  - **接口名称：** 获取主页本周课表
  - **请求路径：** `/api/v1/schedule/current`
  - **请求方法：** `GET`
  - **认证要求：** 需要 Token

- **请求参数**

  *(无 Query 或 Body 参数，后端直接解析 Header 中的 Token 获取学号，并调取 Redis 中的教务 SESSION 抓取数据)*

- **响应数据 (200 OK):**

  - **说明：** 后端已将教务系统复杂的原始 JSON 清洗为前端友好的结构数组。

JSON

```JSON
{
  "code": 200,
  "msg": "获取课表成功",
  "data": [
    {
      "courseName": "数据结构",
      "teacher": "张教授",
      "classroom": "翡翠湖校区某教301",
      "dayOfWeek": 1,
      "section": "1-2"
    },
    {
      "courseName": "C++程序设计",
      "teacher": "王老师",
      "classroom": "科教楼205",
      "dayOfWeek": 3,
      "section": "3-4"
    }
  ]
}
```

- **调用示例 (cURL)**

Bash

```Bash
curl -X GET "https://api.yourdomain.com/api/v1/schedule/current" \
     -H "Authorization: Bearer eyJhbGciOiJIUzI1..."
```

------

### 三、 获取/搜索 OSS 资料列表

- **接口基本信息**
  - **接口名称：** 查询复习资料与搜索
  - **请求路径：** `/api/v1/materials`
  - **请求方法：** `GET`
  - **认证要求：** 需要 Token
- **请求参数 (Query)**

| **参数名** | **类型** | **是否必填** | **说明**                                                     | **示例**     |
| ---------- | -------- | ------------ | ------------------------------------------------------------ | ------------ |
| `parentId` | Integer  | 否           | 父级目录ID。默认 `0` 获取根目录。当传入 `keyword` 时此参数失效。 | `101`        |
| `keyword`  | String   | 否           | 搜索关键词。触发全局模糊搜索。                               | `"数据结构"` |

- **响应数据 (200 OK):**

JSON

```JSON
{
  "code": 200,
  "msg": "查询成功",
  "data": [
    {
      "id": 105,
      "fileName": "大一下期末真题集",
      "isFolder": 1,
      "fileSize": null,
      "ossUrl": null
    },
    {
      "id": 106,
      "fileName": "数据结构必背算法.pdf",
      "isFolder": 0,
      "fileSize": 1048576,
      "ossUrl": "https://hfuture-oss.aliyuncs.com/doc/algo.pdf"
    }
  ]
}
```