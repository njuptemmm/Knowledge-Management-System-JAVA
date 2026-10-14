# 知识管理系统 后端 API

- **Base URL**：`http://localhost:8090`
- **Content-Type**：除文件上传外均为 `application/json; charset=UTF-8`
- **鉴权**：登录后拿到 token，后续请求放在 `Authorization` 头（**不加 Bearer 前缀**）
- **字符编码**：所有请求与响应均为 UTF-8

---

## 0. 通用约定

### 统一响应体

```json
{ "code": 0, "message": "ok", "data": {} }
```

`data` 为空时不序列化该字段。

### 响应码

| code | 含义 | 前端建议动作 |
|---|---|---|
| `0` | 成功 | 正常处理 `data` |
| `1001` | 参数错误 | 直接展示 `message`（含字段名） |
| `1002` | 未登录 / 登录过期 | 清除本地 token，跳登录页 |
| `1003` | 无权限 | 展示 `message` |
| `1004` | 资源不存在 | 展示 `message` |
| `2000` | 一般业务失败 | 直接展示 `message` |
| `5000` | 系统异常 | 展示"系统繁忙，请稍后重试"（真实原因不外泄） |

**HTTP 状态码**：

| 情形 | HTTP | 说明 |
|---|---|---|
| 业务失败、参数错误、未登录、无权限 | `200` | 靠 `code` 区分 |
| 系统异常 | `500` | `code=5000`，真实原因只在服务端日志 |
| 请求路径不存在 | `404` | `code=1004`。路径拼错不是业务结果，给真正的 404 便于排查 |
| 上传文件超过 20MB | `200` | `code=1001`，提示"文件不能超过 20MB" |

除 404 与 500 外，前端**只需判断 `code`**，不必判断 HTTP 状态。

### 分页响应

```json
{
  "code": 0, "message": "ok",
  "data": {
    "records": [ ... ],
    "total": 20,
    "size": 10,
    "current": 1,
    "pages": 2
  }
}
```

### 前端错误处理建议

axios 响应拦截器里统一处理：

```js
if (res.data.code === 1002) { /* 清 token，跳登录 */ }
else if (res.data.code !== 0) { /* 弹 message */ }
```

---

## 1. 认证

### POST `/api/auth/login`

**请求**

```json
{ "username": "admin", "password": "admin123" }
```

**响应**

```json
{
  "code": 0, "message": "ok",
  "data": {
    "token": "a5213414d06a4d1db6659133c912ac05",
    "user": { "id": 1, "username": "admin", "nickname": "管理员", "role": "ADMIN" }
  }
}
```

**失败**：`2000` 用户名或密码错误（用户不存在与密码错误返回同一提示）／`2000` 账号已被禁用

### POST `/api/auth/logout`

请求头带 token 即可，无请求体。登出后该 token 立即失效。

### GET `/api/auth/me`

返回当前登录用户，结构同 login 响应里的 `user`。未登录返回 `1002`。

---

## 2. 分类

### GET `/api/categories/tree` （公开）

返回完整分类树，已按 `sort` 排序。

```json
{
  "code": 0, "message": "ok",
  "data": [
    {
      "id": 1, "name": "编程语言", "parentId": 0, "level": 1, "sort": 1,
      "description": "编程语言相关知识",
      "children": [
        { "id": 2, "name": "Java", "parentId": 1, "level": 2, "sort": 1, "children": [
          { "id": 4, "name": "Spring", "parentId": 2, "level": 3, "sort": 1, "children": [] }
        ]}
      ]
    }
  ]
}
```

### POST `/api/categories` （需登录）

```json
{ "name": "CSAPP", "parentId": 1, "sort": 9, "description": "深入理解计算机系统" }
```

`parentId` 为 `0` 或省略表示根分类。`level` 由服务端按父分类推导，**前端不要提交**。

**失败**：`2000` 父分类不存在／`2000` 同级下已存在同名分类

### PUT `/api/categories/{id}` （需登录）

只改 `name` / `sort` / `description`。**`parentId` 变更会被忽略**（v0.0.1 不支持移动分类），前端编辑表单应将父分类设为只读展示。

### DELETE `/api/categories/{id}` （需登录）

**失败**：`2000` 该分类下有 N 个子分类，请先处理子分类／`2000` 该分类下有 N 个知识条目，无法删除

---

## 3. 知识条目

### GET `/api/knowledge` （公开）

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `categoryId` | long | — | 分类筛选，**包含全部子孙分类** |
| `tagId` | long | — | 标签筛选 |
| `status` | string | — | `DRAFT` / `PUBLISHED` / `ARCHIVED` |
| `mine` | bool | `false` | `true` 时返回当前用户全部状态的条目 |
| `page` | int | 1 | |
| `size` | int | 10 | 上限 100 |

**可见性规则**（服务端强制，前端不必过滤）：

- 未登录，或 `mine=false` → 只返回 `PUBLISHED`
- 已登录且 `mine=true` → 返回 `PUBLISHED` + 自己的 `DRAFT` / `ARCHIVED`

**响应**

```json
{
  "code": 0, "message": "ok",
  "data": { "records": [
    { "id": 9, "title": "MySQL 索引原理", "summary": "B+ 树与最左前缀原则",
      "status": "PUBLISHED", "categoryId": 11, "categoryName": "MySQL",
      "authorName": "管理员", "viewCount": 402, "updatedAt": "2026-10-13 20:59:00" }
  ], "total": 17, "size": 10, "current": 1, "pages": 2 }
}
```

### GET `/api/knowledge/{id}` （公开）

返回详情，**并使 `view_count` 自增**。前端不要在列表渲染时循环调用本接口。

```json
{ "id": 9, "title": "...", "summary": "...", "content": "# Markdown 正文",
  "contentType": "MARKDOWN", "status": "PUBLISHED",
  "categoryId": 11, "categoryName": "MySQL",
  "authorId": 1, "authorName": "管理员", "viewCount": 403,
  "tags": ["实战", "进阶"],
  "createdAt": "2026-10-13 20:59:00", "updatedAt": "2026-10-13 20:59:00" }
```

`categoryName`、`authorName`、`tags` 均由服务端补齐：分类与标签归本模块所有直接查，
作者昵称经 `security` 模块导出的 `UserQueryPort` 取（不跨模块直查 `user` 表）。
条目未设分类时 `categoryName` 为 `null`，无标签时 `tags` 为空数组。

### POST `/api/knowledge` （需登录）

```json
{ "title": "标题", "summary": "摘要", "content": "# 正文",
  "contentType": "MARKDOWN", "categoryId": 2, "tagIds": [1, 3] }
```

新建的条目状态固定为 `DRAFT`，作者取当前登录用户。

### PUT `/api/knowledge/{id}` （需登录，仅作者或管理员）

请求体同上。**失败**：`2000` 无权操作他人的知识条目

### DELETE `/api/knowledge/{id}` （需登录，仅作者或管理员）

会同时清除该条目的标签关联。

### 状态流转（均需登录，仅作者或管理员）

| 方法 | 路径 | 允许的起始状态 | 结果 |
|---|---|---|---|
| POST | `/api/knowledge/{id}/publish` | `DRAFT` | `PUBLISHED` |
| POST | `/api/knowledge/{id}/archive` | `DRAFT` / `PUBLISHED` | `ARCHIVED` |
| POST | `/api/knowledge/{id}/restore` | `ARCHIVED` | `DRAFT` |

非法跃迁返回 `2000`，message 形如 `当前状态不允许发布`。
**前端应按当前状态禁用/隐藏不可用的操作按钮**，而不是让用户点了再报错。

---

## 4. 检索

### GET `/api/search` （公开）

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `keyword` | string | — | 关键词 |
| `categoryId` | long | — | 含子孙分类 |
| `tagId` | long | — | |
| `contentType` | string | — | `MARKDOWN` 等 |
| `status` | string | — | |
| `from` | date | — | `yyyy-MM-dd`，按更新时间 |
| `to` | date | — | `yyyy-MM-dd`，含当天 |
| `sort` | string | `relevance` | `relevance` / `latest` / `hottest` |
| `mine` | bool | `false` | 含义同列表接口 |
| `page` / `size` | int | 1 / 10 | 上限 100 |

**响应**

```json
{ "code": 0, "message": "ok",
  "data": { "records": [
    { "id": 9, "title": "MySQL 索引原理", "snippet": "B+ 树与最左前缀原则",
      "categoryId": 11, "categoryName": "MySQL",
      "tags": ["实战", "进阶"], "status": "PUBLISHED",
      "updatedAt": "2026-10-13 20:59:00", "score": 8.078 }
  ], "total": 2, "size": 10, "current": 1, "pages": 1 } }
```

**`snippet` 是纯文本，不含任何 HTML 标签。高亮由前端完成**——后端不拼 HTML，从根源避免 XSS。前端应把关键词做 HTML 转义后再包 `<em>`，不要用 `v-html` 直接渲染 `snippet`。

**`score` 的量纲取决于关键词**（这是刻意的）：

| 关键词 | 路径 | score 含义 |
|---|---|---|
| 含中文字符 | ngram 全文索引 | MySQL 计算的 TF-IDF 分数（通常 1 ~ 10+） |
| 纯英文/数字 | LIKE 匹配 | 固定权重：标题 3.0 / 摘要 2.0 / 正文 1.0，可叠加 |

**前端不要对 score 做数值假设**（例如"大于 5 算高相关"）。只按后端返回的顺序展示即可。

> 背景：MySQL 的 ngram 解析器**不索引拉丁字符**，`MATCH('Java')` 恒返回 0 条。
> 因此纯英文关键词走 LIKE 兜底，否则整个英文技术词汇都搜不到。详见设计文档 §5.2 第 3 点。

---

## 5. 标签

### GET `/api/tags` （公开）

```json
{ "code": 0, "message": "ok",
  "data": [
    { "id": 2, "name": "进阶", "itemCount": 10 },
    { "id": 1, "name": "入门", "itemCount": 8 }
  ] }
```

按引用次数降序。标签**没有独立的增删改接口**：在保存知识条目时通过 `tagIds`（id 形式）或后续扩展的名称形式提交即可。

---

## 6. 附件

### POST `/api/attachments/upload` （需登录，仅条目作者或管理员）

`multipart/form-data`

| 字段 | 类型 | 说明 |
|---|---|---|
| `itemId` | long | 关联的知识条目 id |
| `file` | file | 文件本体 |

**允许的扩展名**：`pdf` `doc` `docx` `md` `markdown` `txt` `png` `jpg` `jpeg` `gif` `webp` `mp4`
**大小上限**：20MB

**响应**（注意 `parseStatus` 初始为 `PENDING`）

```json
{ "code": 0, "message": "ok",
  "data": { "id": 1, "itemId": 1, "originalName": "设计文档.pdf",
            "mimeType": "application/pdf", "sizeBytes": 52418,
            "parseStatus": "PENDING", "parseError": null,
            "textPreview": null, "createdAt": "2026-10-13 20:59:00" } }
```

**失败**：`2000` 上传文件为空／`1001` 文件不能超过 20MB／`2000` 不支持的文件类型：exe／`2000` 无权操作他人的知识条目

> 超过 20MB 由 Spring 的 multipart 限制在进入 Controller 前拦截，返回 `1001`；
> 略小于 20MB 但超过自定义阈值时由 `LocalFileStorage` 拦截，同样返回可读提示。

### GET `/api/attachments/{id}/text` （需登录）

轮询此接口获取解析进度。`parseStatus` 取值：

| 值 | 含义 | 前端展示 |
|---|---|---|
| `PENDING` | 排队中 | "等待解析…" |
| `PARSING` | 解析中 | "解析中…" |
| `DONE` | 完成 | 展示 `textPreview` |
| `FAILED` | 失败 | 展示 `parseError` |

`textPreview` 为抽取文本的前 200 字（超出加省略号）。

> `.doc`（旧版 Word）**可以上传和下载，但解析一定失败**，`parseError` 为 `不支持的文件类型：doc`。前端对这种文件不应显示进度条，直接提示即可。

### GET `/api/attachments/item/{itemId}` （需登录）

某条目下的全部附件列表。

### GET `/api/attachments/{id}/download` （需登录）

返回文件二进制流，`Content-Disposition` 头含 RFC 5987 编码的原始文件名。
前端用 `<a href>` 或 `window.open` 触发即可（注意带上 Authorization 头需要走 blob 方式）。

### DELETE `/api/attachments/{id}` （需登录，仅条目作者或管理员）

同时删除磁盘文件。

---

## 7. 智能问答（RAG，v0.1.0 新增）

> 未配置密钥时该功能整体关闭，`/api/qa/ask` 会返回一个 `error` 事件说明原因。
> **其余接口完全不受影响。**

### POST `/api/qa/ask` （公开）

**用 POST + JSON 而不是 GET**：问题可以很长，中文经 URL 编码后长度会膨胀数倍，
放在查询参数里会撞上 URL 长度上限并返回 400（实测 1500 字即触发）。

**请求**

```json
{ "question": "MySQL 索引用了什么数据结构？为什么？", "chatId": "可选，续接已有会话" }
```

`chatId` 省略时服务端会新建一个并在 `done` 事件里回传。

**响应：`text/event-stream`**

| event | data | 出现次数 |
|---|---|---|
| `sources` | `[{itemId, title, headingPath, score}]` | 一次，最先发出 |
| `delta` | 字符串片段 | 多次，逐字追加 |
| `done` | `{"chatId": "..."}` | 一次，结束 |
| `error` | `{"message": "..."}` | 出错时，之后流结束 |

**引用来源先于答案发出**，因此前端可以立刻渲染引用卡片，用户先看到"系统找到了什么"，
再等答案逐字出现——而不是盯着空白等几秒。

**前端消费示例**

```js
// 必须用 fetch，不能用 EventSource：
// EventSource 不能自定义请求头，无法携带 Authorization
const resp = await fetch('http://localhost:8090/api/qa/ask', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
  body: JSON.stringify({ question, chatId })
})
const reader = resp.body.getReader()
const decoder = new TextDecoder()
let buffer = ''
for (;;) {
  const { done, value } = await reader.read()
  if (done) break
  buffer += decoder.decode(value, { stream: true })
  // 按空行切分 SSE 事件
  const parts = buffer.split('\n\n')
  buffer = parts.pop()
  for (const part of parts) {
    const event = /event:(.*)/.exec(part)?.[1]?.trim()
    const data = /data:(.*)/.exec(part)?.[1]?.trim()
    if (!event || !data) continue
    if (event === 'sources') setSources(JSON.parse(data))
    else if (event === 'delta') appendAnswer(JSON.parse(data))
    else if (event === 'error') showError(JSON.parse(data).message)
  }
}
```

> **注意**：答案正文里的 `[1]` `[2]` 是引用标记，对应 `sources` 数组的下标加一。
> 前端应把它们渲染成可点击的引用角标，点击跳转到 `/api/knowledge/{itemId}`。

### GET `/api/qa/history/{chatId}` / DELETE `/api/qa/history/{chatId}`

返回/清空会话历史。每条消息形如：

```json
{ "role": "assistant", "content": "答案正文…",
  "sources": [{"itemId": 9, "title": "MySQL 索引原理", "headingPath": "为什么用 B+ 树", "score": 0.71}],
  "at": "2026-10-14T01:49:00.123" }
```

会话保留 7 天，单会话最多 40 条消息。

### GET `/api/qa/status` （公开）

```json
{ "code": 0, "message": "ok",
  "data": { "enabled": true, "indexedItems": 17, "indexedChunks": 56,
            "lastRebuildAt": "2026-10-14T01:49:00.315" } }
```

`enabled=false` 时前端应隐藏问答入口或提示未配置。

### POST `/api/qa/reindex` （需登录）

全量重建索引。**耗时十余秒**（17 个条目约 12 秒），前端必须给出明确的等待反馈，
不能只显示一个转圈图标而没有文案。

**问答的可见性规则与列表、检索接口完全一致**：未登录只能基于已发布内容；
草稿与归档条目的内容既不在索引里，召回后还会再按真实权限过滤一次。

---

## 8. 接口清单速查

| 方法 | 路径 | 鉴权 |
|---|---|---|
| POST | `/api/auth/login` | 公开 |
| POST | `/api/auth/logout` | 需登录 |
| GET | `/api/auth/me` | 需登录 |
| GET | `/api/categories/tree` | 公开 |
| POST | `/api/categories` | 需登录 |
| PUT | `/api/categories/{id}` | 需登录 |
| DELETE | `/api/categories/{id}` | 需登录 |
| GET | `/api/knowledge` | 公开（未登录只见 PUBLISHED） |
| GET | `/api/knowledge/{id}` | 公开 |
| POST | `/api/knowledge` | 需登录 |
| PUT | `/api/knowledge/{id}` | 需登录（作者/管理员） |
| DELETE | `/api/knowledge/{id}` | 需登录（作者/管理员） |
| POST | `/api/knowledge/{id}/publish` | 需登录（作者/管理员） |
| POST | `/api/knowledge/{id}/archive` | 需登录（作者/管理员） |
| POST | `/api/knowledge/{id}/restore` | 需登录（作者/管理员） |
| GET | `/api/search` | 公开（未登录只见 PUBLISHED） |
| POST | `/api/qa/ask` | 公开（未登录只见 PUBLISHED 内容） |
| GET | `/api/qa/history/{chatId}` | 需登录 |
| DELETE | `/api/qa/history/{chatId}` | 需登录 |
| GET | `/api/qa/status` | 公开 |
| POST | `/api/qa/reindex` | 需登录 |
| GET | `/api/tags` | 公开 |
| POST | `/api/attachments/upload` | 需登录（作者/管理员） |
| GET | `/api/attachments/{id}/text` | 需登录 |
| GET | `/api/attachments/item/{itemId}` | 需登录 |
| GET | `/api/attachments/{id}/download` | 需登录 |
| DELETE | `/api/attachments/{id}` | 需登录（作者/管理员） |

---

## 9. 测试账号（来自 `sql/seed.sql`）

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `zhangsan` | `user123` | USER |

`zhangsan` 用于验证越权路径：他修改/删除 `admin` 的条目会被拒绝。

---

## 10. 已知限制

1. **分类不可移动**：`PUT /api/categories/{id}` 会忽略 `parentId`。前端编辑表单应把父分类设为只读展示。
2. **混合关键词检索**：如 `Java 索引`，因含中文会归入 ngram 路径，只命中中文部分（`Java` 部分丢失）。纯英文或纯中文关键词均正常。
3. **分类树未做缓存**：每次 `GET /api/categories/tree` 都查全表。课程设计数据规模下无感知，数据量增长后应加缓存。
4. **`view_count` 无防刷**：同一用户反复打开详情页会持续累加。
