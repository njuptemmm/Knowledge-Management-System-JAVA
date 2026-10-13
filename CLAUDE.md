# 知识管理系统（KMS）— 项目约定

> **权限声明**
>
> 本文件是**项目级**约定，其权限**从属于**工作区根目录的 `claude code/CLAUDE.md`。
> 根 CLAUDE.md 具有**根本性质权限**，其中的「第一性原理」「约束先行」「交互设计原则」「开发习惯」「Git 与部署」「安全审查」「Skill 管理」等条款对本项目**完全适用且优先级更高**。
>
> **本文件不得与根 CLAUDE.md 的任何描述相悖。** 若两者出现冲突，以根 CLAUDE.md 为准，并须立即修正本文件。
> 本文件只做**补充**：在根约定基础上，增加本项目特有的结构与技术约定。

---

## 一、本项目为什么重要

本项目是**南京邮电大学 专业课程设计II**（2026-2027 第一学期）的正式交付物，题目 1「知识管理系统」。

它有双重约束，且**两者同等重要**：

1. **工程约束**：要在 2 周内（2026.10.12 – 2026.10.23）单人交付一套可运行、可演示的知识管理系统
2. **考核约束**：项目同时是课程设计报告的素材来源，报告占分极重，**验收答辩会逐项问询程序结构、类关系、函数调用关系**

**这一条决定了本项目的核心工作方式**：代码不只是「能跑就行」，它必须能被解释、能被画成图、能被写进报告。
任何「为了快点跑通而写的、事后说不清楚的代码」，在本项目里是**负资产**——它会在答辩环节直接扣分。

因此：

- 每个模块的实现必须能清楚回答：它做什么、怎么用、依赖谁
- 关键算法必须同步产出流程图/伪代码
- 测试数据必须可复现（`sql/seed.sql`），不能靠临时手工造
- 报告**与实现并行产出**，不允许堆到最后两天补

## 二、设计与计划文档

| 文档 | 位置 | 说明 |
|---|---|---|
| 设计文档（spec） | `docs/spec/kms-design-v0.0.1.md` | **本项目的权威设计来源**，实现以它为准 |
| 实现计划（plan） | `docs/plan/` | 由 spec 派生，kebab-case 命名 |
| 报告草稿与图 | `docs/report/` | ER 图、用例图、流程图、UI 原型、报告各章节草稿 |

> 设计文档在工作区根目录 `docs/superpowers/specs/2026-10-13-kms-design.md` 另存一份镜像，
> 用于与工作区其他项目 spec 保持一致。**本项目仓库内的副本为准**；修改设计时两处同步更新。

**改设计必须先改 spec，再改代码**，不要反过来（根 CLAUDE.md「约束先行」原则）。

## 三、目录结构约定

```
java-homework/
├── CLAUDE.md                 # 本文件
├── files/                    # 课程原始材料（docx 原件 + 转换后的 md），只读，不修改
├── docs/
│   ├── spec/                 # 设计文档（权威）
│   ├── plan/                 # 实现计划
│   └── report/               # 报告草稿与插图
├── kms-backend/              # Spring Boot 后端（独立 Maven 工程）
└── kms-frontend/             # Vue3 前端（独立 npm 工程）
```

### 3.1 后端包结构（按业务域切分，域内再分层）

```
com.njupt.kms
├── common/       # M0 平台支撑：Result、异常、常量、工具
├── config/       # M0：Web、Redis、MyBatis-Plus 配置
├── security/     # M0：认证拦截器、登录上下文
├── knowledge/    # M1 知识资产域
├── ingestion/    # M2 内容接入域
└── search/       # M3 检索域
```

**不使用全局的 `controller/` / `service/` 大包。** 那样会让模块边界在代码层面消失，直接损害答辩时的"模块结构描述"。

### 3.2 模块所有权铁律

> **模块只能读写自己拥有的表。跨模块取数一律通过对方导出的接口或端口。**

| 模块 | 拥有表 |
|---|---|
| M0 平台支撑 | `user` |
| M1 知识资产域 | `knowledge_item` `category` `tag` `item_tag` |
| M2 内容接入域 | `attachment` |
| M3 检索域 | 无（经 M1 导出的只读端口 `KnowledgeSearchPort` 取数） |

越界的检查方式：任何 Mapper 中出现非本模块表的 SQL，即为违规。

### 3.3 命名约定

| 类型 | 约定 | 示例 |
|---|---|---|
| 数据表 | 小写下划线 | `knowledge_item` |
| Java 类 | 大驼峰 | `KnowledgeServiceImpl` |
| 接口 | 不加 `I` 前缀 | `KnowledgeService` |
| 实体 | 与表名对应的单数驼峰 | `KnowledgeItem` |
| REST 路径 | `/api/` + 小写复数 | `/api/knowledge` |
| 前端组件 | 大驼峰 | `CategoryTree.vue` |
| 前端视图 | 大驼峰 + `View` 后缀 | `KnowledgeDetailView.vue` |

## 四、技术栈（不得随意更换）

| 层 | 选型 | 版本 |
|---|---|---|
| 后端 | Spring Boot | 3.5.3 |
| 语言 | Java | 17 |
| 持久层 | MyBatis-Plus | 3.5.10.1 |
| 数据库 | MySQL | 8.x |
| 缓存 | Redis | — |
| 前端 | Vue 3 + Vite | 3.4+ / 6.x |
| UI 库 | naive-ui | 2.41+ |
| 状态管理 | Pinia | 3.x |
| HTTP 客户端 | axios | — |

更换任何一项技术栈，须先改本文件与 spec，再动代码。

## 五、代码与数据约定

- **数据库变更一律走 SQL 脚本**：建表写 `kms-backend/sql/schema.sql`，测试数据写 `kms-backend/sql/seed.sql`。不使用 Hibernate 自动建表
- **中文全文检索必须带 `WITH PARSER ngram`**，且 `title` / `summary` / `content` 各建**独立**全文索引（原因见 spec §5.2）
- **统一响应体**：所有接口返回 `Result<T>`（`code` / `message` / `data`），不裸返实体
- **异常统一处理**：业务失败抛 `BusinessException`；系统异常的真实原因**绝不回传前端**，只记日志
- **前端所有网络请求收口于 `src/api/`**，组件内不得直接发请求
- **高亮在前端做**，后端只返回纯文本片段，避免后端拼 HTML 引入 XSS

## 六、密钥与配置

- 数据库密码、API Key 等**严禁**写入代码或提交 git
- 敏感配置放 `kms-backend/src/main/resources/application-local.yaml`，该文件**必须在 `.gitignore` 中**
- 提交时提供 `application-local.yaml.example` 模板，含占位符与获取说明
- 用户密码使用 **BCrypt** 哈希存储，禁止明文或可逆加密

## 七、构建与运行

```bash
# 后端（在 kms-backend/ 下）
mvn spring-boot:run             # 启动，监听 8090
mvn test                        # 跑测试

# 前端（在 kms-frontend/ 下）
npm install
npm run dev                     # 开发服务器
npm run build                   # 生产构建
```

**端口约定：后端固定 8090。** 本机 8080 已被其他服务占用（实测），不要改回 8080。
前端 `src/api/` 的 baseURL 必须与之一致。

**数据库脚本必须带字符集参数执行**，否则 Windows 的 mysql 客户端会按控制台编码解析文件，中文写入失败：

```bash
mysql --default-character-set=utf8mb4 -u root -p < sql/schema.sql
mysql --default-character-set=utf8mb4 -u root -p < sql/seed.sql
```

改完代码**必须主动验证**（test / build / 手工走通主流程），不只改不验。
验证时使用**对抗性思路**：除正常路径外，一并验证越权、边界输入、状态机非法跃迁、重复提交。

## 八、交付物

课程要求的交付物（命名含学号，见 spec §13.1）：

| 类别 | 命名 |
|---|---|
| 报告文档 | `专业课程设计II报告_学号.doc` |
| 附件压缩包（设计文件 + 源代码） | `专业课程设计II附件_学号.rar` |

**因此 `docs/` 下的设计与报告材料必须随仓库一起维护**——它们本身就是交付物的一部分，不是临时文件。

## 九、Git 约定

- 仓库：`https://github.com/njuptemmm/Knowledge-Management-System-JAVA`
- commit message 用英文，简洁描述变更意图
- **push 前等待确认**（根 CLAUDE.md 约定：`git push` 仅用于跨设备同步，不自动执行）
- 不提交 `target/`、`node_modules/`、`uploads/`、`application-local.yaml`

## 十、安全审查

功能或项目完成后，**主动提示用户是否进行安全审查**（不得自动执行）。
本项目涉及用户输入处理、认证鉴权、文件上传与 SQL 拼接，属高触发场景。
用户确认后调用 `codex-security` skill 执行扫描；Critical / High 级别问题必须先修复。
