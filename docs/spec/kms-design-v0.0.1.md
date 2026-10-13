# 知识管理系统（KMS）设计文档 · v0.0.1

- **课程**：南京邮电大学 专业课程设计II（2026-2027 第一学期）
- **题目**：题目 1 — 知识管理系统
- **指导教师**：柯昌博
- **周期**：2026.10.12 – 2026.10.23（单人独立完成）
- **状态**：待实现
- **文档版本**：v0.0.1（纯 MVP，可选项全部延后）

---

## 1. 背景与目标

### 1.1 任务来源

任务书要求为《专业课程设计II》课程搭建一套知识管理系统，含四项要求（见 [任务书.md:57-69](../../../software/java-homework/files/专业课程设计II题目-任务书.md#L57-L69)）：

1. **知识库** —— 存储技术报告、文档、论文、视频等知识资产，支持搜索、浏览、分类、归档；应注重知识的内在结构（如层次关系），可用本体等方式建模
2. **搜索引擎** —— 全文搜索与高级搜索（"支持基于知识库的大模型自动问答（RAG）" 标注为**可选**）
3. **内容管理系统（CMS）** —— 创建、编辑、发布、管理知识内容，支持多种文件格式与多媒体
4. **系统界面** —— 美观、使用友好

### 1.2 本项目目标

v0.0.1 的目标只有一个：**在 2 周内交付一套功能完整、可演示、可答辩的知识管理系统，并同步产出课程设计报告所需的全部设计材料。**

设计取舍一律服从于评分表（[报告模板.md:54-69](../../../software/java-homework/files/专业课程设计II-报告模板-2026-2027-1.md#L54-L69)）所反映的验收标准，而非技术偏好。

### 1.3 从评分表倒推的设计约束

| 评分项 | 设计要求 | 对架构的约束 |
|---|---|---|
| 3. 人机交互设计意识，合理友好，操作简便 | 界面是评分对象本身 | 前端纳入核心模块，非遗留项 |
| 2. 数据结构与算法设计描述，关键算法流程图/伪代码 | 系统必须包含真实算法 | 分类树构建、检索排序须可画流程图 |
| 5. 充分测试 | 需有测试数据与用例设计 | 测试材料随实现同步产出 |
| 6. 可持续发展（安全环保） | 密码加密、权限、输入校验 | 认证鉴权不可省略 |
| 8. 报告详实、格式规范 | 报告占分极重 | 设计产物须可直接填入报告章节 |

**推论**：报告与实现并行产出。每个实现阶段结束即填写对应报告章节，不留到最后补。

---

## 2. 范围

### 2.1 v0.0.1 交付（In Scope）

| 编号 | 功能 | 对应任务书要求 |
|---|---|---|
| F1 | 用户登录 / 登出 / 身份校验 | 评分项 6（安全） |
| F2 | 知识条目 CRUD + 草稿/发布/归档状态流转 | 知识库、CMS |
| F3 | 分类树管理（多级层次） | 知识库（层次关系） |
| F4 | 标签管理与条目打标 | 知识库（分类） |
| F5 | 文件上传（PDF / Word / Markdown / 图片）+ 文本解析 | CMS（多格式） |
| F6 | 全文检索（中文） | 搜索引擎 |
| F7 | 高级检索（分类 × 标签 × 类型 × 时间 × 状态 组合过滤 + 排序 + 高亮） | 搜索引擎 |
| F8 | 前端全部界面（浏览、详情、编辑、搜索、管理） | 系统界面 |

### 2.2 v0.0.1 明确不做（Out of Scope）

以下均为**经评估后主动砍掉**，不是遗漏，全部记入 §12 后续版本。

| 项 | 砍掉理由 |
|---|---|
| RAG 智能问答 | 任务书标注"可选"。向量库调试风险高，会挤占测试与报告时间 |
| 条目间语义关联 `item_relation` | "本体建模"由 F3 分类树满足；语义关联是加分项，非必需 |
| 知识版本管理 | 非任务书要求 |
| 视频解析 | 需转码 + 抽帧（FFmpeg），投入产出比过低。视频仅作附件存储 |
| Elasticsearch | 中文分词器安装、索引同步一致性、JVM 调优会吃掉一半工期 |
| 微服务拆分 | 单人 2 周，服务发现/网关/分布式事务成本必为负收益 |
| 闭包表优化分类树 | 邻接表 + 应用层递归已满足需求；优化方案写入报告作"方案对比"素材 |
| 富文本所见即所得编辑器 | Markdown 编辑器足以交付，且更易与全文检索配合 |

---

## 3. 体系结构

### 3.1 风格

**模块化单体（Modular Monolith）+ 分层架构（Layered Architecture）**。

- 单一部署单元（一个 Spring Boot 进程 + 一个前端静态包）
- 内部按业务域划分模块，模块间通过 Java 接口通信，依赖倒置
- 各层职责：Controller（协议适配）→ Service（业务规则）→ Mapper（持久化）

选择理由：单人 2 周工期下，模块化单体保留了"清晰的模块边界"这一答辩要点，同时不引入任何分布式运维成本。

### 3.2 模块划分

```
┌──────────────────────────────────────────────────────┐
│  M4 前端交互层  (Vue3 SPA)                            │
└───────────────────────┬──────────────────────────────┘
                        │ REST / JSON  (§4.1)
┌───────────────────────▼──────────────────────────────┐
│  M1 知识资产域      M2 内容接入域      M3 检索域        │
│  KnowledgeService   IngestionService   SearchService  │
│  CategoryService    ParserFactory                     │
│  TagService                                           │
│  ┌────────────────────────────────────────────────┐  │
│  │  M0 平台支撑（横切）：认证 / Result / 异常 / 日志 │  │
│  └────────────────────────────────────────────────┘  │
└───────────────────────┬──────────────────────────────┘
                        │ MyBatis-Plus / Spring Data Redis
        ┌───────────────┴───────────────┐
   ┌────▼─────┐                   ┌─────▼────┐
   │ MySQL 8  │                   │  Redis   │
   └──────────┘                   └──────────┘
```

#### M1 知识资产域 —— 系统的记录源头（system of record）

| 项 | 内容 |
|---|---|
| 职责 | 知识条目与分类标签的完整生命周期：CRUD、状态流转、分类树、打标 |
| 拥有数据 | `knowledge_item` `category` `tag` `item_tag` |
| 对外接口 | `KnowledgeService` `CategoryService` `TagService` |
| 对外应用服务 | `KnowledgeQueryService`（浏览/检索视角）、`ContentAuthoringService`（创作/发布视角） |
| 依赖 | 仅依赖 M0 与持久层，**不依赖 M2/M3** |

**关于"知识库"与"CMS"的边界决定**：任务书将二者列为并列要求，但数据上是同一实体 `knowledge_item` 的两个视图。若拆为两个模块，会立刻产生"谁拥有 `knowledge_item` 表"的归属冲突。因此合为单一 M1，通过两个应用服务区分视角。

#### M2 内容接入域 —— 处理器（processor），非记录源头

| 项 | 内容 |
|---|---|
| 职责 | 接收上传文件 → 按类型选择解析器 → 抽取文本 → 回吐给 M1 存储 |
| 拥有数据 | `attachment` |
| 对外接口 | `IngestionService`、`ParserFactory` |
| 依赖 | M0、持久层、文件系统 |

**边界铁律**：M2 不感知分类与标签的存在，只负责"把文件变成文本"。此边界一旦模糊，M1/M2 会迅速纠缠成一体。

#### M3 检索域

| 项 | 内容 |
|---|---|
| 职责 | 全文检索、高级检索（多条件组合过滤）、排序、分页、命中片段提取 |
| 拥有数据 | 无 |
| 对外接口 | `SearchService.search(SearchQuery) → Page<SearchHit>` |
| 依赖 | `KnowledgeSearchPort`（由 M1 实现并导出的只读查询端口） |

v0.0.1 中 M3 不建独立索引，直接基于 MySQL 全文索引查询。**它是模块而非 M1 的一个方法**，因为高级检索的条件组合、排序融合、命中片段提取具备独立复杂度，且拥有独立接口契约。

**关于跨模块读取的边界处理**：§3.3 规定模块不得直接读他人表。检索天然需要跨表查询，若强行绕道 `KnowledgeService` 的通用 CRUD 接口会既低效又别扭。因此 M1 额外导出一个**只读端口** `KnowledgeSearchPort`：

```java
// M1 导出，M3 消费。只读，不含任何写操作。
public interface KnowledgeSearchPort {
    Page<KnowledgeRow> search(FullTextCondition condition, Pageable pageable);
}
```

端口由 M1 实现（其 Mapper 落在 M1 包内），M3 只见接口。这样"表的所有权"仍完整留在 M1，而检索所需的复杂 SQL 由拥有该表的模块自己书写。这是对 §3.3 铁律的**受控例外**，且例外的方向是收敛的（M3 永远不直接持有 `knowledge_item` 的 Mapper）。

#### M4 前端交互层

| 项 | 内容 |
|---|---|
| 职责 | 全部用户可见界面与交互 |
| 对外接口 | 消费后端 REST API |
| 依赖 | 仅依赖后端 HTTP 契约，不感知后端模块划分 |

页面清单（对应报告"2.4 主要功能界面设计"的原型）：

| 编号 | 页面 | 承载功能 | 关键交互 |
|---|---|---|---|
| P1 | 登录页 | F1 | 用户名密码登录 |
| P2 | 知识浏览首页 | F3、F6 | 左侧分类树 + 右侧条目列表 + 顶部搜索框 |
| P3 | 知识详情页 | F2、F5 | Markdown 渲染、附件列表与下载/预览、浏览数自增 |
| P4 | 知识编辑器 | F2、F4、F5 | Markdown 编辑与预览、分类选择、标签输入、附件上传、保存草稿/发布 |
| P5 | 检索结果页 | F6、F7 | 关键词高亮、多维过滤器（分类/标签/类型/时间/状态）、排序切换 |
| P6 | 我的知识库 | F2 | 我的草稿/已发布/已归档，状态流转操作 |
| P7 | 分类管理页 | F3 | 分类树增删改，层级拖拽或表单编辑 |

交互原则（对应评分项 3"操作简便"）：

- 分类树、标签、状态等选项**由系统推断或预填**，不让用户记忆和手填
- 有损操作（删除、归档）二次确认，且提示影响范围（如"该分类下有 3 个条目"）
- 空态给出引导而非空白（如"还没有知识条目，去创建第一篇"）
- 列表加载使用骨架屏，避免布局跳动

#### M0 平台支撑（横切关注点）

认证鉴权、统一响应体 `Result<T>`、全局异常处理、参数校验、日志、CORS。不构成业务模块。

### 3.3 模块所有权规则

> **铁律：模块只能读写自己拥有的表。跨模块取数一律通过对方导出的接口或只读端口。**

| 模块 | 拥有表 |
|---|---|
| M0 平台支撑 | `user` |
| M1 知识资产域 | `knowledge_item` `category` `tag` `item_tag` |
| M2 内容接入域 | `attachment` |
| M3 检索域 | 无 |

违反此规则的检查方式：任何 Mapper 中出现非本模块表的 SQL，即为越界（`AdminMapper` 等跨表统计查询亦不例外，须改由拥有该表的模块提供接口）。

M3 需要跨表检索，其取数路径见 §3.2 的受控例外：M1 导出只读端口 `KnowledgeSearchPort`，针对 `knowledge_item` 的 SQL 仍写在 M1 包内，M3 只见接口与 M1 定义的行对象。依赖方向单向——`search` 依赖 `knowledge`，反之不成立。

---

## 4. 模块间通信

分四类，不可混谈。

### 4.1 M4 前端 ↔ 后端

| 场景 | 方式 |
|---|---|
| 常规 CRUD | `REST + JSON`，`Content-Type: application/json` |
| 文件上传 | `multipart/form-data` |
| 认证 | HTTP Header `Authorization: <token>` |
| 文件下载 | 后端返回二进制流 + `Content-Disposition` |

前端所有请求收口于 `src/api/` 目录，组件不得直接发起网络请求。

### 4.2 模块 ↔ 模块（同进程）

**Java 接口调用 + 依赖倒置**。调用方注入接口类型，实现类由 Spring 容器装配。

```java
// M3 依赖 M1 的接口，而非实现
public class SearchServiceImpl implements SearchService {
    private final KnowledgeService knowledgeService;   // 接口
}
```

此手法保证：更换实现（如 M1 的存储方式变更）不会波及 M3。参考项目 `ChatHistoryRepository` 的 InMemory / Redis 双实现即此模式的活样板。

### 4.3 模块 ↔ 基础设施

| 依赖 | 方式 |
|---|---|
| MySQL | MyBatis-Plus + HikariCP 连接池 |
| Redis | Spring Data Redis（Lettuce）；用于登录会话与分类树缓存 |

### 4.4 跨模块异步

文件解析**必须异步**（大文件解析可能耗时数十秒，同步会触发 HTTP 超时）。

- 触发：M2 上传完成 → 发布 `AttachmentUploadedEvent`
- 监听：M2 内部 `@TransactionalEventListener` 异步消费 → 解析 → 回写 `attachment.parsed_text` 与 `parse_status`
- 状态回推：前端轮询 `GET /api/attachments/{id}/text`，依据 `parseStatus` 展示进度

v0.0.1 中该异步**不跨模块**（生产者与消费者均在 M2 内），保留出去是为了后续 RAG 阶段（M3 消费解析结果建索引）时无需改造。

---

## 5. 数据模型

### 5.1 表结构

```sql
-- 用户
user (
  id            BIGINT PK AUTO_INCREMENT,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,        -- BCrypt
  nickname      VARCHAR(50),
  role          VARCHAR(20)  NOT NULL DEFAULT 'USER',   -- USER / ADMIN
  status        TINYINT      NOT NULL DEFAULT 1,        -- 1 启用 / 0 禁用
  created_at    DATETIME     NOT NULL,
  updated_at    DATETIME     NOT NULL
)

-- 分类树（邻接表）
category (
  id          BIGINT PK AUTO_INCREMENT,
  name        VARCHAR(50)  NOT NULL,
  parent_id   BIGINT       NOT NULL DEFAULT 0,   -- 0 表示根
  level       INT          NOT NULL DEFAULT 1,   -- 层级，从 1 起
  sort        INT          NOT NULL DEFAULT 0,   -- 同级排序
  description VARCHAR(255),
  created_at  DATETIME     NOT NULL,
  updated_at  DATETIME     NOT NULL,
  INDEX idx_parent (parent_id)
)

-- 知识条目
knowledge_item (
  id           BIGINT PK AUTO_INCREMENT,
  title        VARCHAR(200) NOT NULL,
  summary      VARCHAR(500),                    -- 摘要，检索加权更高
  content      LONGTEXT,                        -- Markdown 正文
  content_type VARCHAR(20)  NOT NULL DEFAULT 'MARKDOWN',
  status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT/PUBLISHED/ARCHIVED
  category_id  BIGINT,
  author_id    BIGINT       NOT NULL,
  view_count   INT          NOT NULL DEFAULT 0,
  created_at   DATETIME     NOT NULL,
  updated_at   DATETIME     NOT NULL,
  published_at DATETIME,
  archived_at  DATETIME,
  INDEX idx_category (category_id),
  INDEX idx_status (status),
  INDEX idx_author (author_id),
  -- 三个独立全文索引：MySQL 要求 MATCH() 的列必须与某个全文索引的列完全一致，
  -- 故无法用单个复合索引实现分列加权（见 §6.3）
  FULLTEXT INDEX ft_title   (title)   WITH PARSER ngram,
  FULLTEXT INDEX ft_summary (summary) WITH PARSER ngram,
  FULLTEXT INDEX ft_content (content) WITH PARSER ngram
)

-- 标签
tag (
  id         BIGINT PK AUTO_INCREMENT,
  name       VARCHAR(50) NOT NULL UNIQUE,
  created_at DATETIME    NOT NULL
)

-- 条目-标签关联
item_tag (
  item_id BIGINT NOT NULL,
  tag_id  BIGINT NOT NULL,
  PRIMARY KEY (item_id, tag_id),
  INDEX idx_tag (tag_id)
)

-- 附件
attachment (
  id            BIGINT PK AUTO_INCREMENT,
  item_id       BIGINT       NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  stored_path   VARCHAR(500) NOT NULL,
  mime_type     VARCHAR(100),
  size_bytes    BIGINT       NOT NULL DEFAULT 0,
  parsed_text   LONGTEXT,
  parse_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING/PARSING/DONE/FAILED
  parse_error   VARCHAR(500),
  created_at    DATETIME     NOT NULL,
  INDEX idx_item (item_id)
)
```

### 5.2 四个必须落实的技术点

1. **`FULLTEXT ... WITH PARSER ngram`**
   MySQL 默认按空格分词，对中文几乎无效（整句会被当作一个 token）。不加 `ngram` 解析器，中文全文检索等同于废功能。建表时必须带上。

   分词粒度由系统变量 `ngram_token_size` 控制，默认 2（二元组）。对中文是合理默认值，**不做修改**；若出现召回偏差，在报告中记为调优过程。

2. **必须建三个独立全文索引，而非一个复合索引**
   MySQL 要求 `MATCH(col...)` 中的列与某个全文索引的列**完全一致**。因此 `FULLTEXT(title, summary, content)` 这个复合索引**无法**支持 `MATCH(title)` 单独求相关度——§6.3 的分列加权将直接报错。

   故分别建立 `ft_title`、`ft_summary`、`ft_content` 三个索引。代价是索引空间增加，收益是标题命中的权重可独立调节。

3. **ngram 解析器不索引拉丁字符 —— 必须做双路径检索**（实测确认）

   本项目在 MySQL 8.0.41 上实测：`ngram_token_size=2` 时，**ngram 解析器只对 CJK 字符生成 token，拉丁字母与数字序列完全不进索引**。

   ```
   MATCH(title) AGAINST('基础')  →  得分 1.69   ✅
   MATCH(title) AGAINST('Java')  →  得分 0      ❌
   MATCH(title) AGAINST('Jav')   →  得分 0      ❌
   MATCH(title) AGAINST('ava')   →  得分 0      ❌
   ```

   自然语言模式、布尔模式（`+Java`、`Jav*`）、短语模式，以及换到 `content` 列检索，**全部返回 0 条**。

   **影响**：本系统是编程知识库，标题大量含 `Java`、`MySQL`、`Redis`、`Spring`、`JVM`、`Python`。若只建 ngram 索引，用户搜索任何英文技术名词都搜不到结果——这是不可接受的缺陷。

   **方案**：`SearchService` 按关键词字符集选择查询路径。

   | 关键词 | 路径 | 依据 |
   |---|---|---|
   | 含 CJK 字符 | `MATCH ... AGAINST`（走 ngram 索引） | 索引有效，相关度排序准确 |
   | 纯 ASCII | `LIKE '%kw%'` 匹配 title / summary / content | 绕开 ngram 的拉丁盲区 |

   两条路径**共用同一套加权公式**（§6.3），因此相关度语义一致。

   **被否决的替代方案**：为拉丁词另建三个默认分词器的全文索引。由于 MySQL 要求 `MATCH()` 的列与索引列完全一致，且同一列上无法并存两个不同解析器的全文索引，必须增加三个 STORED 生成列（内容列需复制 LONGTEXT），schema 复杂度与存储代价不成比例。`LIKE` 在课程设计的数据规模（数百条）下性能无差异。

   **已知局限**：混合关键词（如 `Java 基础`）会归入 CJK 路径，只命中中文部分。记入报告第七部分作为后续改进点。

4. **检索范围与状态的一致性**
   只有 `status = 'PUBLISHED'` 的条目对外可见并进入默认检索范围；作者本人可检索自己的 `DRAFT`。此规则由 `SearchService` 统一收口，不允许各调用点自行判断。

### 5.3 实体关系（ER 图底稿）

```
user 1 ──── n knowledge_item        (author_id)
category 1 ─ n knowledge_item       (category_id)
category 1 ─ n category             (parent_id，自关联，树)
knowledge_item n ─ n tag            (经 item_tag)
knowledge_item 1 ─ n attachment     (item_id)
```

---

## 6. 关键算法

本节对应评分项 2（"给出数据结构和算法的设计描述、关键算法的流程图或伪代码、各算法之间的结构关系描述"）。实现时须同步产出**流程图**，用于报告第三部分。

### 6.1 分类树构建（邻接表 → 树，单次查询）

朴素实现按层递归查询数据库，n 级分类产生 n 次查询（N+1 问题）。本设计采用**一次全表查询 + 内存组装**：

```
输入：全部分类记录 List<Category>（含 id、parentId）
输出：树形结构 List<CategoryNode>

1. 遍历 records，建立映射 nodeMap: id → node，同时初始化每个 node 的 children 为空列表
2. 遍历 records：
     if parentId == 0:   加入 roots
     else:               nodeMap[parentId].children.add(node)
3. 对 roots 递归排序（按 sort 字段，同层有序）
4. 返回 roots
```

- 时间复杂度：O(n log n)（排序主导），相比递归查询的 O(n × depth) 数据库往返
- 数据结构：`HashMap<Long, CategoryNode>` + 树形链表

### 6.2 分类路径计算

新增分类时需维护 `level` 与展示路径。由 `parentId` 向上回溯至根：

```
输入：parentId
输出：level

if parentId == 0:  return 1
parent ← 查询 parentId 对应分类
if parent == null: 抛异常（父分类不存在）
return parent.level + 1
```

### 6.3 检索排序与加权融合

MySQL 的 `MATCH ... AGAINST` 返回相关度分数，但**单个索引只能对其定义的列集合整体求分**。为使标题命中优先于正文命中，本设计建了三个独立全文索引（§5.2 第 2 点），从而可分列求分并加权融合：

```sql
SELECT k.*,
       3.0 * MATCH(k.title)   AGAINST (:kw IN NATURAL LANGUAGE MODE)
     + 2.0 * MATCH(k.summary) AGAINST (:kw IN NATURAL LANGUAGE MODE)
     + 1.0 * MATCH(k.content) AGAINST (:kw IN NATURAL LANGUAGE MODE)  AS score
FROM knowledge_item k
WHERE MATCH(k.title) AGAINST (:kw IN NATURAL LANGUAGE MODE)
   OR MATCH(k.summary) AGAINST (:kw IN NATURAL LANGUAGE MODE)
   OR MATCH(k.content) AGAINST (:kw IN NATURAL LANGUAGE MODE)
ORDER BY score DESC, k.updated_at DESC
```

最终排序：`ORDER BY score DESC, updated_at DESC`（相关度优先，同分时新者优先）。

#### 6.3.1 双路径：拉丁关键词的降级

由于 ngram 解析器不索引拉丁字符（§5.2 第 3 点），上式的 `MATCH` 路径对纯英文关键词恒为 0 分，会导致搜索 `Java` 返回空结果。因此 `SearchService` 按关键词字符集分派到两条 SQL 路径：

**路径 A — 含 CJK 字符**：使用上式的 `MATCH ... AGAINST`，走 ngram 全文索引，相关度为 MySQL 计算的 TF-IDF 分数。

**路径 B — 纯 ASCII**：使用 `LIKE` 匹配，相关度按命中列**固定赋权**：

```sql
SELECT k.*,
       (CASE WHEN k.title   LIKE CONCAT('%', :kw, '%') THEN 3.0 ELSE 0 END
      + CASE WHEN k.summary LIKE CONCAT('%', :kw, '%') THEN 2.0 ELSE 0 END
      + CASE WHEN k.content LIKE CONCAT('%', :kw, '%') THEN 1.0 ELSE 0 END) AS score
FROM knowledge_item k
WHERE k.title LIKE CONCAT('%', :kw, '%')
   OR k.summary LIKE CONCAT('%', :kw, '%')
   OR k.content LIKE CONCAT('%', :kw, '%')
ORDER BY score DESC, k.updated_at DESC
```

两条路径的**权重取值相同**（标题 3.0 / 摘要 2.0 / 正文 1.0），因此"标题命中优先于正文命中"这一语义在两种关键词下保持一致。

**路径选择的判定**：关键词中存在任一 `Character.UnicodeScript.HAN`（或广义 CJK 区间）字符即走路径 A，否则走路径 B。

**路径 B 的 `LIKE` 无法使用索引**，是全表扫描。在课程设计的数据规模（数百条）下实测无感知差异；数据量增长后应改为 §5.2 第 3 点所述的生成列方案。

权重取值依据：标题是内容的最强语义浓缩，摘要次之，正文兜底。权重作为**配置项**（`app.search.weight.title` 等）而非硬编码，便于报告中对"参数调优"作说明。

**实现注意**：三列加权求和意味着同一行会被三次全文匹配，成本高于单索引查询。数据量为课程设计规模（数百条），可忽略；报告中作为"检索方案权衡"的记录点。

### 6.4 关键词高亮

```
输入：原文 text、关键词 keyword、上下文长度 radius
输出：含 <em> 标记的片段

1. 在 text 中定位 keyword 首次出现位置 pos
2. if pos == -1: 返回 text 前 2×radius 字符 + "…"
3. 截取 [pos - radius, pos + radius + len(keyword)] 区间
4. 区间内所有 keyword 出现处包裹 <em> 标签
5. 首尾不足处补 "…"
```

**安全要求**：高亮在**前端**执行（Vue 层），后端只返回纯文本与命中位置，避免后端拼接 HTML 引入 XSS 风险。若要后端返回 HTML，则必须先做 HTML 转义再包裹标签——v0.0.1 选择前者，从源头消除风险。

---

## 7. 接口契约

统一响应体：

```json
{ "code": 0, "message": "ok", "data": { } }
```

`code` 非 0 表示业务失败，`message` 为可直接展示的中文提示。

### 7.1 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录，返回 token |
| POST | `/api/auth/logout` | 登出，清除 token |
| GET | `/api/auth/me` | 获取当前用户信息 |

### 7.2 分类

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/categories/tree` | 获取完整分类树 |
| POST | `/api/categories` | 新建分类 |
| PUT | `/api/categories/{id}` | 修改分类 |
| DELETE | `/api/categories/{id}` | 删除分类（含子分类或条目的分类禁止删除） |

### 7.3 知识条目

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/knowledge` | 分页浏览，支持 `categoryId` `tagId` `status` `mine` `page` `size` |
| GET | `/api/knowledge/{id}` | 详情，`view_count` 自增 |
| POST | `/api/knowledge` | 新建（默认 `DRAFT`） |
| PUT | `/api/knowledge/{id}` | 编辑 |
| DELETE | `/api/knowledge/{id}` | 删除（仅作者或管理员） |
| POST | `/api/knowledge/{id}/publish` | 发布 |
| POST | `/api/knowledge/{id}/archive` | 归档 |
| POST | `/api/knowledge/{id}/restore` | 从归档恢复 |

### 7.4 检索

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/search` | 高级检索 |

参数：`keyword` `categoryId` `tagId` `contentType` `status` `from` `to` `sort` `mine` `page` `size`

`sort` 取值：`relevance`（默认） / `latest` / `hottest`

**`categoryId` 的语义**：筛选**包含该分类的全部子孙分类**，而非仅精确匹配。点击"Java"分类应能看到"Spring"下的条目。实现上复用 §6.1 的树算法求后代 id 集合。

**`mine` 的语义**：`mine=true` 时返回当前用户的**全部状态**条目（含草稿与归档），否则只返回 `PUBLISHED`。这是前端 P6「我的知识库」的必要支撑。§7.3 列表接口的 `mine` 参数含义相同。

返回体 `SearchHit` 字段：`id` `title` `snippet` `categoryId` `categoryName` `tags` `status` `updatedAt` `score`。

`snippet` 为**纯文本**命中片段（不含任何 HTML 标签），高亮由前端完成，理由见 §6.4。

### 7.5 附件

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/attachments/upload` | 上传（multipart，参数 `itemId` `file`） |
| GET | `/api/attachments/{id}/text` | 获取解析结果与 `parseStatus` |
| GET | `/api/attachments/{id}/download` | 下载原文件 |
| DELETE | `/api/attachments/{id}` | 删除附件 |

### 7.6 标签

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/tags` | 标签列表（含使用计数） |

标签的创建随条目保存时一并提交，不提供独立 CRUD 端点。

---

## 8. 技术选型

| 层 | 选型 | 版本 | 理由 |
|---|---|---|---|
| 后端语言 | Java | 17 | 参考项目已验证，与 Spring Boot 3 匹配 |
| 后端框架 | Spring Boot | 3.5.3 | 与参考项目同构，骨架可直接借鉴 |
| 持久层 | MyBatis-Plus | 3.5.10.1 | 单表 CRUD 免写 SQL，复杂查询手写 |
| 数据库 | MySQL | 8.x | 全文索引 + ngram 满足中文检索 |
| 缓存 | Redis | — | 会话存储 + 分类树缓存 |
| 前端框架 | Vue | 3.4+ | 参考项目已验证 |
| 构建 | Vite | 6.x | — |
| UI 组件库 | naive-ui | 2.41+ | 组件完备，出界面快（对应评分项 3） |
| 状态管理 | Pinia | 3.x | — |
| HTTP 客户端 | axios | — | 拦截器统一注入 token 与错误处理 |
| Markdown 渲染 | marked + DOMPurify | — | 渲染 + XSS 过滤（对应评分项 6） |

### 8.1 对参考项目的复用边界

参考项目 `software/ai-study-helper` 提供了可用的骨架。复用与自研的界限必须清楚：

| 可借鉴 | 必须自研 |
|---|---|
| 分层骨架与包结构 | 分类树算法（§6.1、§6.2） |
| `Result<T>` 统一响应、全局异常处理 | 检索排序与加权融合（§6.3） |
| 前端 `api/` 层收口模式、路由与视图组织 | 全部业务实体与业务规则（M1） |
| 配置文件分离敏感信息（`application-local.yaml`） | 全部前端界面 |
| Redis 配置与序列化 | 文件解析流程与状态机（M2） |

**理由**：答辩环节将问询"程序结构描述、类与类之间的关系、函数调用关系"。照搬而未理解的部分在此环节必然暴露。复用基础设施骨架可节省时间，但领域逻辑必须自研且能逐行解释。

---

## 9. 工程结构

```
software/java-homework/
├── CLAUDE.md                     # 项目级约定（实现前必须先建立）
├── files/                        # 课程原始文档（docx + 转换后的 md）
├── docs/
│   ├── spec/                     # 设计文档
│   └── report/                   # 报告草稿与图（ER 图、用例图、流程图、UI 原型）
├── kms-backend/                  # Spring Boot 后端
│   ├── pom.xml
│   ├── sql/
│   │   ├── schema.sql            # 建表
│   │   └── seed.sql              # 测试数据
│   └── src/main/java/com/njupt/kms/
│       ├── KmsApplication.java
│       ├── common/               # M0：Result、异常、常量、工具
│       ├── config/               # M0：Web、Redis、MyBatis-Plus 配置
│       ├── security/             # M0：认证拦截器、登录上下文
│       ├── knowledge/            # M1：知识资产域
│       │   ├── controller/
│       │   ├── service/          # 接口 + impl
│       │   ├── mapper/
│       │   └── entity/
│       ├── ingestion/            # M2：内容接入域
│       │   ├── controller/
│       │   ├── service/
│       │   ├── parser/           # ParserFactory + 各类型解析器
│       │   ├── event/
│       │   └── entity/
│       └── search/               # M3：检索域
│           ├── controller/
│           ├── service/
│           └── dto/
└── kms-frontend/                 # Vue3 前端
    ├── package.json
    └── src/
        ├── api/                  # 全部后端调用收口于此
        ├── router/
        ├── stores/               # Pinia
        ├── views/                # 页面
        ├── components/           # 通用组件（分类树、Markdown 渲染器、高亮等）
        └── utils/
```

包结构按**业务域**划分（`knowledge` / `ingestion` / `search`），域内再分层。不使用全局 `controller` / `service` 大包——那样会让模块边界在代码层面消失。

---

## 10. 错误处理

### 10.1 统一机制

| 异常类型 | HTTP 状态 | 处理 |
|---|---|---|
| `BusinessException` | 200 | 返回 `{code: 非0, message}`，前端直接展示 message |
| 参数校验失败（`@Validated`） | 200 | 返回对应字段的中文提示 |
| 未认证 | 401 | 前端跳转登录页 |
| 无权限 | 403 | 提示"无权操作" |
| 系统异常 | 500 | 记录完整堆栈到日志，仅向用户返回"系统繁忙，请稍后重试" |

系统异常的**真实原因绝不回传前端**（避免泄露堆栈与内部结构，对应评分项 6）。

### 10.2 关键业务规则

| 规则 | 处理 |
|---|---|
| 删除含子分类的分类 | 拒绝，提示"请先处理子分类" |
| 删除仍被条目引用的分类 | 拒绝，提示引用数量 |
| 非作者删除他人条目 | 拒绝（管理员除外） |
| 上传超限文件 | 拒绝，提示上限（配置 20MB，与 §7.5 一致） |
| 解析失败 | 记录 `parse_error`，`parse_status = FAILED`，不阻塞条目本身 |

---

## 11. 测试策略

对应评分项 5 与报告第五部分。测试材料与实现同步产出，不得事后补编。

### 11.1 层次

| 层次 | 范围 | 工具 |
|---|---|---|
| 单元测试 | 分类树构建、路径计算、加权排序、高亮片段 | JUnit 5 |
| 接口测试 | 各 REST 端点的正常与异常路径 | MockMvc |
| 功能测试 | 全链路场景，人工执行并记录 | 手工用例表 |

### 11.2 测试数据

`sql/seed.sql` 提供固定数据集，保证测试可复现：

- 用户：2 个（普通用户 + 管理员）
- 分类：至少 3 级层次，覆盖根 / 中间 / 叶子
- 知识条目：≥ 20 条，覆盖三种状态与多种文件类型
- 附件：覆盖 PDF / Word / Markdown / 图片各 ≥ 1 个
- 边界数据：空内容条目、超长标题、含特殊字符与单引号的标题

含特殊字符的标题是必要用例——检索与 SQL 拼接的问题通常在此暴露。

### 11.3 对抗性验证要求

每项功能完成后，除正常路径外必须验证：

- 越权路径（他人草稿、他人条目删除、未登录访问）
- 边界输入（空值、超长、特殊字符、非法 ID）
- 状态机非法跃迁（如归档态直接编辑发布）
- 并发与重复提交（重复上传、重复发布）

---

## 12. 后续版本（Backlog）

以下为 v0.0.1 主动延后项，按预估价值排序。架构已为其预留扩展点。

| 优先级 | 项 | 预留的扩展点 |
|---|---|---|
| P0 | RAG 智能问答 | M3 已是独立模块与独立接口；`attachment.parsed_text` 已是切块输入源；异步事件机制已就位 |
| P1 | 条目间语义关联（本体关系） | M1 新增 `item_relation` 表与 `RelationService`，不影响既有表 |
| P1 | 分类树闭包表优化 | `category` 表结构可平滑迁移，查询接口不变 |
| P2 | 知识版本管理 | `knowledge_item` 增 `version` 字段 + `item_version` 历史表 |
| P2 | 视频内容解析 | `ParserFactory` 新增视频解析器实现即可接入 |
| P3 | Elasticsearch 检索 | `SearchService` 为接口，可新增 ES 实现类替换 |

---

## 13. 交付物与验收映射

### 13.1 课程要求的交付物

见 [报告模板.md:160-170](../../../software/java-homework/files/专业课程设计II-报告模板-2026-2027-1.md#L160-L170)：

| 类别 | 内容 | 命名 |
|---|---|---|
| 电子 | 报告文档 | `专业课程设计II报告_学号.doc` |
| 电子 | 附件压缩包（分析设计文件 + 源代码） | `专业课程设计II附件_学号.rar` |
| 纸质 | 报告打印版 | 封面与评分项**正反两面打在一页** |
| 现场 | 验收答辩 | 见 §13.3 |

### 13.2 报告章节与设计产物映射

| 报告章节 | 由本设计的哪部分产出 |
|---|---|
| 一、课题内容和要求 | §1、§2 |
| 二、需求分析和总体设计 | §2（用例）、§3（体系结构）、§5（ER 图）、§3.2 M4 页面清单（界面原型） |
| 三、相关功能模块详细设计 | §6（数据结构 + 流程图）、§9（包结构） |
| 四、部分核心代码 | 按 §6 算法实现处截图 |
| 五、软件测试及其结果分析 | §11 |
| 六、遇到的问题及解决方法 | 实现过程记录（如 ngram 配置、中文检索调优、异步解析状态回推） |
| 七、总结 | 须紧扣评分表 10 项与教学大纲思考题 |

**第七部分特别提示**：评分表要求总结涵盖"查阅资料与自学、团队交流、项目管理与经济决策、可持续发展（安全环保）"等在第一至六部分无法体现的要点。单人完成的情况下，"团队协作"应转为对**个人项目管理与任务分解**的反思（如 §13.4 排期如何制定与调整）。

### 13.3 答辩准备材料

任务书要求的四项答辩内容（[任务书.md:129-135](../../../software/java-homework/files/专业课程设计II题目-任务书.md#L129-L135)）对应准备：

| 答辩内容 | 准备物 |
|---|---|
| 功能演示（界面友好性 + 功能完备性） | 演示脚本，覆盖 F1–F8 主流程 |
| 程序结构描述 | 模块结构图、类关系图、函数调用关系、数据结构选型说明 |
| 测试数据准备情况 | `seed.sql` 与测试用例表 |
| 典型问题及解决方案 | 实现日志（问题 → 定位 → 方案 → 验证） |

### 13.4 实现排期

| 阶段 | 时间 | 产出 | 同步填写的报告章节 |
|---|---|---|---|
| 需求与设计 | D1–D2 | 用例图、ER 图、体系结构图、UI 原型 | 一、二 |
| 库表与后端核心 | D3–D5 | 建表、M1 全部功能、认证 | 三 |
| 接入与检索 | D6–D8 | M2 上传解析、M3 检索、前端主界面 | 三、四 |
| 前端完善 | D9–D10 | 全部页面与交互细节、响应式与空态 | 二（原型回填） |
| 测试 | D11–D12 | 单元/接口/功能测试、测试用例表 | 五、六 |
| 报告与答辩 | D13–D14 | 报告定稿、演示脚本、答辩材料 | 七 |

日期：D1 = 2026.10.12，D14 = 2026.10.23。

---

## 14. 实现前置条件

实现开始前必须完成（依据根 `CLAUDE.md`"约束先行"原则）：

1. 建立 `software/java-homework/CLAUDE.md`，声明本项目约定与根 CLAUDE.md 的根本性权限关系
2. 初始化本项目独立 git 仓库
3. 确认开发环境：JDK 17、Maven、MySQL 8、Redis、Node.js 18+

以上三项完成后方可进入实现阶段。
