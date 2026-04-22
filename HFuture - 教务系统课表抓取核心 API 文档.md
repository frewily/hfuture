# HFuture - 教务系统 (EAMS5) 课表抓取核心 API 文档

**版本:** v1.0

**业务场景:** 模拟用户登录合工大 CAS 统一认证，穿透 SSO 鉴权，最终抓取全量课表数据。

**全局认证规范:** 本链路属于严格的 **状态机模式 (State Machine)**。所有的身份认证均依赖 HTTP `Cookie` 头的上下文传递，调用方（后端服务）必须全局维护一个 Cookie Jar 来实现会话保持。

------

## 阶段一：统一身份认证 (CAS) 获取全局凭证

### 1. 初始化 CAS 会话 (获取 Cookie 与 execution)

- **接口名称:** 获取初始会话状态
- **请求路径:** `http://cas.hfut.edu.cn/cas/login`
- **请求方法:** `GET`
- **认证方式:** 无（起点接口）

**请求参数:**

| **参数名** | **类型** | **必填** | **说明**           | **示例** |
| ---------- | -------- | -------- | ------------------ | -------- |
| 无         | -        | -        | 此接口无需主动传参 | -        |

**响应数据:**

- **类型:** `HTML / 文本`

- **提取目标:** 

  1. 响应头 `Set-Cookie` 中的 `SESSIONID=[待填写]` 等初始饼干。

  2. HTML 源码中的隐藏表单字段：`<input type="hidden" name="execution" value="[提取该值]" />`

**调用示例 (cURL):**

```Bash
curl --location --request GET 'http://cas.hfut.edu.cn/cas/login'
```

------

### 2. 获取图片验证码 & LOGIN_FLAVORING (风控收集)

> **注：** 这两个步骤通常是并行的 GET 请求，用于完善 CAS 登录前的 Cookie 完整度。

- **接口名称:** 获取前置风控 Cookie 组合

- **请求路径:** 

  -	验证码: `http://cas.hfut.edu.cn/cas/vercode`

  - FLAVORING: `http://cas.hfut.edu.cn/cas/checkInitParams`

- **请求方法:** `GET`

- **认证方式:** `Cookie` (需携带第一步获取的 `SESSIONID`)

**响应数据:**

- **提取目标:** 响应头 `Set-Cookie` 中的 `JSESSIONID` 和 `LOGIN_FLAVORING`。
- **成功示例:** HTTP 200 OK，返回图片流或风控标识，伴随 Set-Cookie。

------

### 3. 提交 CAS 账号密码登录

- **接口名称:** 执行 CAS 统一登录
- **请求路径:** `http://cas.hfut.edu.cn/cas/login`
- **请求方法:** `GET`
- **认证方式:** `Cookie` (必须携带前两步集齐的所有 Cookie：SESSION，JSESSIONID，LOGIN_FLAVORING)

**请求参数 (Body form-data):**

| **参数名** | **类型** | **必填** | **说明**                             | **示例**     |
| ---------- | -------- | -------- | ------------------------------------ | ------------ |
| username   | String   | 是       | 学生学号                             | `2025xxxxxx` |
| password   | String   | 是       | 经过 **AES ECB算法加密**后的密码密文 | `XXXX==`     |
| execution  | String   | 是       | 第 1 步在网页源码中提取出的流水号    | `e1s1`       |
| _eventId   | String   | 是       | 固定标识                             | `submit`     |
| captcha    | String   | 是       | 第 2 步获取的图片验证码              | `abcd`       |

**响应数据:**

- **成功判定:** 返回 HTTP 状态码 **`200 OK`**，显示cas协议登录成功跳转页面。
- **提取目标:** 在响应头的 `Set-Cookie` 中获取全局最高权限凭证：**`TGC=...`**。
- **失败示例:** 返回 HTTP `200` 并附带 HTML 登录首页（代表账号密码错或验证码错误或 execution 过期）。

------

## 阶段二：教务系统 (SSO) 鉴权与会话激活

### 4. 申请教务系统专属 Ticket

- **接口名称:** 获取教务系统 Ticket
- **请求路径:** `http://cas.hfut.edu.cn/cas/login`
- **请求方法:** `GET`
- **认证方式:** `Cookie` (必须携带全局凭证 `TGC`)

**请求参数 (Query):**

| **参数名** | **类型** | **必填** | **说明**                    | **示例**                                                     |
| ---------- | -------- | -------- | --------------------------- | ------------------------------------------------------- |
| service    | String   | 是       | 教务系统的 SSO 验证接收地址 | `http://jxglstu.hfut.edu.cn/eams5-student/neusoft-sso/login` |

**响应数据:**

- **提取目标:** 返回 HTTP `302`，从响应头 `Location` 中提取完整 URL 里的 `ticket=ST-XXX...`。
- **注意事项:** 获取到的 `ST-` 开头的票据极易过期（通常<60秒），且仅能使用一次。

------

### 5. 换取并激活教务 SESSION (核心通行证)

- **接口名称:** 换取并激活教务鉴权 Cookie
- **请求路径:** `http://jxglstu.hfut.edu.cn/eams5-student/neusoft-sso/login`
- **请求方法:** `GET`
- **认证方式:** 票据验证 (URL 传参)

**请求参数 (Query):**

| **参数名** | **类型** | **必填** | **说明**             | **示例**               |
| ---------- | -------- | -------- | -------------------- | ---------------------- |
| ticket     | String   | 是       | 上一步获取的专属票据 | `ST-123456-abcdefg...` |

**响应数据 & 核心鉴权机制:**

- **鉴权坑点揭秘 (Session Hydration):** 教务系统的 SSO 验证分为“发证”和“激活”两步。
  1. 验证 ticket 成功后，网关会下发 `SESSION` 并在服务器内存中创建一个空会话，返回 `HTTP 302`。
  2. **必须让请求跟随 302 重定向**，访问主页（如 `/eams5-student/home`）。主页被访问时，服务器才会将真实的学号、身份信息写入该空会话（即激活会话）。
- **成功判定:** 最终完成跳转链条，返回主页的 **`HTTP 200 OK`**。
- **提取目标:** 在跳转链条第一环（302 响应头）下发的 `Set-Cookie` 中的 **`SESSION=...`**。此时该 Cookie 已在服务端被彻底激活（有效期通常为 3 小时，后续教务接口全靠它）。

**调用示例 (cURL):**

```Bash
# ⚠️ 注意：必须使用 -L 或 --location 参数让 cURL 自动跟随重定向！
curl -L --request GET 'http://jxglstu.hfut.edu.cn/eams5-student/neusoft-sso/login?ticket=ST-XXX...' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
```

**后端开发 (HFuture) 落地指北:**

当使用 Java (如 HttpClient / OkHttp) 或 Python (如 Requests) 编写此节点代码时，**必须确保 HTTP 客户端的“自动跟随重定向 (Follow Redirects)”处于开启状态**。或者在捕获到 302 和 Cookie 后，代码必须主动向 `Location` 指向的地址发起一次 GET 请求以完成激活，切勿拿着未激活的 Cookie 直接请求课表接口。

------

## 阶段三：业务数据获取 (EAMS5 课表拉取)

### 6. 提取学生内部 ID (dataId)

- **接口名称:** 获取业务数据主键
- **请求路径:** `http://jxglstu.hfut.edu.cn/eams5-student/for-std/course-table`
- **请求方法:** `GET`
- **认证方式:** `Cookie` (必须携带上一步拿到的已经激活的教务 `SESSION`)

**响应数据:**

- **成功判定:** 返回 HTTP `302 Found`。
- **提取目标:** 解析响应头 `Location` URL 的末尾数字，例如 `/info/123456` 中的 **`123456`** 即为 `dataId`。
- **错误码含义:** `302` 指向登录页（说明传入的 SESSION 为无效游客状态，需清空缓存重走阶段二）。

------

### 7. 获取全局环境变量 (bizTypeId & semesterId)

- **接口名称:** 获取系统级参数
- **请求路径:** `http://jxglstu.hfut.edu.cn/eams5-student/for-std/course-table/info/{dataId}`
- **请求方法:** `GET`
- **认证方式:** `Cookie` (教务 `SESSION`)

**请求参数 (Path):**

| **参数名** | **类型** | **必填** | **说明**                 | **示例** |
| ---------- | -------- | -------- | ------------------------ | -------- |
| dataId     | Number   | 是       | 第 6 步获取的学生内部 ID | `123456` |

**响应数据:**

- **类型:** `HTML 源码`
- **提取目标:** 使用正则提取潜藏在 JS 对象中的值：
  - 业务类型: bizTypeId: 2 (通常为 2)
    - 正则表达式: `bizTypeId\s*:\s*(\d+)` 
  - 学期 ID: 寻找全局定义的 semesterId (如 334)
  - 注：学期ID有固定规律，如：
    - 2024-2025学年第二学期 value="294"
    - 2025-2026学年第一学期 value="314"
    - 2025-2026学年第二学期 value="334"
    - 2026-2027学年第一学期 value="354"

------

### 8. 获取本学期课程汇总 (LessonIds)

- **接口名称:** 检索课程 ID 列表
- **请求路径:** `http://jxglstu.hfut.edu.cn/eams5-student/for-std/course-table/get-data`
- **请求方法:** `GET`
- **认证方式:** `Cookie` (教务 `SESSION`)

**请求参数 (Query):**

| **参数名** | **类型** | **必填** | **说明**               | **示例** |
| ---------- | -------- | -------- | ---------------------- | -------- |
| bizTypeId  | Number   | 是       | 培养层次 (2=本科生)    | `2`      |
| semesterId | Number   | 是       | 教务系统内部的学期标识 | `334`    |
| dataId     | Number   | 是       | 学生内部 ID            | `123456` |

**响应数据 (JSON):**

| **字段名**  | **类型**      | **说明**                             | **成功示例**      |
| ----------- | ------------- | ------------------------------------ | ----------------- |
| lessonIds   | Array<Number> | 用户本学期绑定的全部课程内部 ID 数组 | `[111, 222, 333]` |
| currentWeek | Number        | 当前处于教学历的第几周               | `8`               |

------

### 9. 获取最终完整课表

- **接口名称:** 获取排课矩阵明细
- **请求路径:** `http://jxglstu.hfut.edu.cn/eams5-student/ws/schedule-table/datum`
- **请求方法:** `POST`
- **认证方式:** `Cookie` (教务 `SESSION`)

**请求头 (Headers):**

- `Content-Type: application/json`
- `Cookie: SESSION=[你的有效会话]`

**请求参数 (Body - Raw JSON):**

| **参数说明** | **类型**    | **必填** | **示例**                                                     |
| ------------ | ----------- | -------- | ------------------------------------------------------------ |
| 全量查询JSON | JSON Object | 是       | 将上一步（第8步 `get-data`）返回的 **整个完整的 JSON 对象** 直接作为请求体原样传入。 |

**Body 参数示例 (必须是上一接口返回的完整结构):**

```json
{
    "timeTableLayoutId": null,
    "weekIndices": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19],
    "lessonId2Flag": {},
    "currentWeek": 8,
    "lesson2CultivateTypeMap": {},
    "lessonIds": [12345, 12346, 12347],
    "lessons": []
}
```

**响应数据 (JSON):**

- **说明:** 返回极其庞大的排课结构，包含每门课程的具体节次、星期、上课地点及教师姓名。

- **成功示例:** HTTP 200 OK 

  ```json
  {
    "result": {
      "lessonList": [
        {
          "id": 123456,
          "code": 0000001X--001, 
          "name": "计算机25-1班"
          "courseName": "数据结构",
          "courseTypeName": "通识必修课",
          "teacherAssignmentList": [
                    {
                        "role": "MAJOR",
                        "code": null,
                        "teacherId": 1234,
                        "personId": 1234,
                        "name": "教师名",
                        "age": null,
                        "titleName": null
                    }
                ],
                "requiredPeriodInfo": {...},
                "actualPeriods": 12,
                "scheduleState": "FULLY_SCHEDULED",
                "limitCount": 123,
                "stdCount": 12,
                "suggestScheduleWeeks": [1,2,3,4,5,6,7,8,9,10],
                "suggestScheduleWeekInfo": "1~10",
                "campusId": 1,
                "roomTypeId": 12,
                "adminclassIds": [
                    1111,
                    2222
                ],
                "remark": null,
                "scheduleRemark": null,
                "adminclasses": [
                    {
                        "id": 1111,
                        "nameZh": "计算机25-1班",
                        "nameEn": "20251234567",
                        "code": "20251234567",
                        "grade": "2025",
                        "stdCount": 34,
                        "planCount": 34,
                        "enabled": true,
                        "abbrZh": null,
                        "abbrEn": null
                    },
                    {
                        "id": 2222,
                        "nameZh": "计算机25-2班",
                        "nameEn": "20252345678",
                        "code": "20252345678",
                        "grade": "2025",
                        "stdCount": 32,
                        "planCount": 32,
                        "enabled": true,
                        "abbrZh": null,
                        "abbrEn": null
                    }
                ],
                "scheduleJsonParams": []
        }
      ]
    }
  }
  ```
  

*(具体 JSON 结构体视抓包实际返回内容为准)*

**调用示例 (cURL):**

```bash
curl --location --request POST 'http://jxglstu.hfut.edu.cn/eams5-student/ws/schedule-table/datum' \
--header 'Content-Type: application/json' \
--header 'Cookie: SESSION=[你的教务SESSION]' \
--data-raw '{
    "timeTableLayoutId": 3,
    "weekIndices": [
    	111,
    	222,
    	333
	]
	...
}'
```

------

### 💡 常见错误码及排查指南

| **现象**         | **HTTP 状态码**  | **含义及排查方向**                                           |
| ---------------- | ---------------- | ------------------------------------------------------------ |
| CAS 登录重定向   | 200 OK login主页 | `登录失败`。通常因为加密密码不对，或者 execution 参数已过期。 |
| 教务请求跳登录页 | 302 指向 login   | `未鉴权/SESSION伪造`。携带的教务 SESSION 是游客级或已过期，需重新用 Ticket 换取。 |
| 接口 404         | 404 Not Found    | `前缀丢失`。请求 URL 漏了 `/eams5-student/` 工程名。         |
