# 知识管理系统 v0.0.1 — 后端实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付一套可独立测试的知识管理系统后端 REST API，覆盖认证、分类树、知识条目、附件解析、全文检索五块能力。

**Architecture:** 模块化单体 + 分层架构。按业务域分包（`knowledge` / `ingestion` / `search`），域内再分层（controller / service / mapper / entity）。模块间通过 Java 接口通信，M1 额外导出只读端口 `KnowledgeSearchPort` 供 M3 跨模块取数。

**Tech Stack:** Java 17 · Spring Boot 3.5.3 · MyBatis-Plus 3.5.10.1 · MySQL 8.0.41 · Redis · PDFBox · Apache POI · JUnit 5 · MockMvc

**Spec:** `docs/spec/kms-design-v0.0.1.md`（权威设计来源）

## Global Constraints

- Java 版本：**17**
- Spring Boot 版本：**3.5.3**（父 POM，不覆盖）
- MyBatis-Plus 版本：**3.5.10.1**
- 基础包名：**`com.njupt.kms`**
- 数据库：**MySQL 8**，库名 **`kms`**，字符集 **`utf8mb4`**
- 所有表必须为 **InnoDB** 引擎
- 中文全文检索索引必须带 **`WITH PARSER ngram`**，且 `title` / `summary` / `content` **各建一个独立全文索引**（不可用复合索引，否则分列 `MATCH()` 报错）
- 所有接口返回 **`Result<T>`**（`code` / `message` / `data`），不裸返实体
- 业务失败抛 **`BusinessException`**；系统异常的真实原因**绝不回传前端**
- 密码必须用 **BCrypt** 哈希存储，禁止明文
- 敏感配置放 **`src/main/resources/application-local.yaml`**，该文件**禁止提交 git**
- 模块只能读写自己拥有的表：`security` 拥有 `user`，`knowledge` 拥有 `knowledge_item` `category` `tag` `item_tag`，`ingestion` 拥有 `attachment`，`search` 无表
- 不使用全局 `controller/` `service/` 大包
- 前端相关的一切**不在本计划范围**（见 `2026-10-13-kms-frontend.md`）

## 对 spec 的补充（本计划确立，spec 后续同步）

实现中发现 spec 有三处未定义，本计划明确如下，实现以本节为准：

1. **`user` 表归属**：spec §3.3 的所有权表未列 `user`。本计划确定由 **`security` 包**拥有。
2. **分类筛选含子孙分类**：spec §7.4 的 `categoryId` 参数未说明是否含子分类。本计划确定**包含全部子孙分类**（点击父分类能看到下级条目，符合用户预期）。实现复用分类树算法求后代 id 集合。
3. **`mine` 参数**：spec §7.3 / §7.4 未定义"只看自己的"。本计划新增可选参数 `mine=true`：为真时返回当前用户的**全部状态**条目（含草稿），为假时只返回 `PUBLISHED`。这是前端 P6「我的知识库」的必要支撑。

---

## 文件结构

```
kms-backend/
├── pom.xml
├── sql/
│   ├── schema.sql                                  # 建表
│   └── seed.sql                                    # 测试数据
└── src/
    ├── main/
    │   ├── java/com/njupt/kms/
    │   │   ├── KmsApplication.java
    │   │   ├── common/
    │   │   │   ├── Result.java                     # 统一响应体
    │   │   │   ├── ResultCode.java                 # 响应码常量
    │   │   │   ├── BusinessException.java          # 业务异常
    │   │   │   └── GlobalExceptionHandler.java     # 全局异常处理
    │   │   ├── config/
    │   │   │   ├── WebConfig.java                  # 拦截器注册、CORS
    │   │   │   └── MybatisPlusConfig.java          # 分页插件
    │   │   ├── security/
    │   │   │   ├── User.java                       # 用户实体
    │   │   │   ├── UserMapper.java
    │   │   │   ├── AuthService.java                # 登录/登出/校验
    │   │   │   ├── AuthController.java
    │   │   │   ├── AuthInterceptor.java            # token 校验
    │   │   │   ├── CurrentUser.java                # ThreadLocal 登录上下文
    │   │   │   └── dto/{LoginRequest,UserVO}.java
    │   │   ├── knowledge/
    │   │   │   ├── entity/{Category,Tag,KnowledgeItem,ItemTag}.java
    │   │   │   ├── mapper/{CategoryMapper,TagMapper,KnowledgeItemMapper,ItemTagMapper}.java
    │   │   │   ├── service/
    │   │   │   │   ├── CategoryService.java
    │   │   │   │   ├── TagService.java
    │   │   │   │   ├── KnowledgeService.java
    │   │   │   │   ├── CategoryTreeBuilder.java    # 纯函数：扁平→树
    │   │   │   │   ├── KnowledgeSearchPort.java    # 导出给 M3 的只读端口
    │   │   │   │   └── impl/{...ServiceImpl}.java
    │   │   │   ├── controller/{CategoryController,TagController,KnowledgeController}.java
    │   │   │   └── dto/...                          # 请求/响应对象
    │   │   ├── ingestion/
    │   │   │   ├── entity/Attachment.java
    │   │   │   ├── mapper/AttachmentMapper.java
    │   │   │   ├── storage/{FileStorage,LocalFileStorage}.java
    │   │   │   ├── parser/{TextParser,ParserFactory,PdfParser,DocxParser,MarkdownParser,ImageParser}.java
    │   │   │   ├── event/AttachmentUploadedEvent.java
    │   │   │   ├── listener/AttachmentParseListener.java
    │   │   │   ├── service/IngestionService.java
    │   │   │   ├── controller/AttachmentController.java
    │   │   │   └── dto/AttachmentVO.java
    │   │   └── search/                              # M3 无 mapper：取数走 KnowledgeSearchPort
    │   │       ├── service/SearchService.java
    │   │       ├── controller/SearchController.java
    │   │       └── dto/{SearchQuery,SearchHit}.java
    │   └── resources/
    │       ├── application.yaml
    │       ├── application-local.yaml.example
    │       ├── mapper/KnowledgeItemMapper.xml      # 列表分页（M1）
    │       └── mapper/KnowledgeSearchMapper.xml    # 全文检索加权（M1）
    └── test/java/com/njupt/kms/
        ├── common/GlobalExceptionHandlerTest.java
        ├── security/AuthServiceTest.java
        ├── knowledge/CategoryTreeBuilderTest.java
        ├── knowledge/CategoryServiceTest.java
        ├── knowledge/KnowledgeServiceTest.java
        ├── knowledge/KnowledgeStatusTest.java
        ├── knowledge/TagServiceTest.java
        ├── ingestion/FileStorageTest.java
        ├── ingestion/ParserFactoryTest.java
        ├── search/SearchQueryBuilderTest.java
        └── ApiSmokeTest.java
```

---

### Task 1: 工程骨架与配置

**Files:**
- Create: `kms-backend/pom.xml`
- Create: `kms-backend/src/main/java/com/njupt/kms/KmsApplication.java`
- Create: `kms-backend/src/main/resources/application.yaml`
- Create: `kms-backend/src/main/resources/application-local.yaml.example`
- Create: `kms-backend/.gitignore`

**Interfaces:**
- Consumes: 无（首个任务）
- Produces: 可编译运行的 Spring Boot 工程骨架；包根 `com.njupt.kms`；配置项 `app.upload-dir`、`app.search.weight.{title,summary,content}`

- [ ] **Step 1: 创建 `kms-backend/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.3</version>
        <relativePath/>
    </parent>

    <groupId>com.njupt</groupId>
    <artifactId>kms-backend</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>kms-backend</name>
    <description>知识管理系统后端（专业课程设计II 题目1）</description>

    <properties>
        <java.version>17</java.version>
        <mybatis-plus.version>3.5.10.1</mybatis-plus.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- BCrypt 密码编码：只引 crypto 模块，不引 starter-security，
             否则会启用整条 Web 安全过滤器链，把所有接口默认拦死 -->
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-crypto</artifactId>
        </dependency>

        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <!-- MyBatis-Plus 3.5.9 起 jsqlparser 被拆分，分页插件必须显式引入 -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 `KmsApplication.java`**

```java
package com.njupt.kms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.njupt.kms.**.mapper")
public class KmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(KmsApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 `application.yaml`**

```yaml
spring:
  application:
    name: kms
  # 真实密钥/密码放 application-local.yaml（已 gitignore）
  profiles:
    active: local
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/kms?serverTimezone=Asia/Shanghai&useSSL=false&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 3000ms
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true

app:
  upload-dir: uploads
  search:
    weight:
      title: 3.0
      summary: 2.0
      content: 1.0

logging:
  level:
    com.njupt.kms: DEBUG
```

- [ ] **Step 4: 创建 `application-local.yaml.example`**

```yaml
# =====================================================================
# 本地敏感配置模板
# 使用方法：
#   1. 复制本文件为 application-local.yaml（同名去掉 .example）
#   2. 填入真实值
#   3. application-local.yaml 已在 .gitignore 中，不会被提交
# =====================================================================

spring:
  datasource:
    username: root
    password: YOUR_MYSQL_PASSWORD

  data:
    redis:
      host: localhost
      port: 6379
      # password: 你的 Redis 密码（本地通常不需要）
```

- [ ] **Step 5: 创建 `kms-backend/.gitignore`**

```
target/
uploads/
application-local.yaml
*.log
.idea/
*.iml
```

- [ ] **Step 6: 创建本地配置并编译验证**

```bash
cd kms-backend
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
# 编辑 application-local.yaml 填入真实 MySQL 密码
mvn -q clean compile
```

Expected: `BUILD SUCCESS`，`target/classes/com/njupt/kms/KmsApplication.class` 存在

- [ ] **Step 7: 提交**

```bash
git add kms-backend/pom.xml kms-backend/.gitignore kms-backend/src
git commit -m "feat(backend): scaffold Spring Boot project with datasource and redis config"
```

---

### Task 2: 数据库 schema 与初始数据

**Files:**
- Create: `kms-backend/sql/schema.sql`
- Create: `kms-backend/sql/seed.sql`

**Interfaces:**
- Consumes: Task 1 的 `kms` 数据库配置
- Produces: 全部 7 张表；`seed.sql` 中的固定测试数据（2 用户 / 多级分类 / 20+ 条目 / 标签）

- [ ] **Step 1: 创建 `sql/schema.sql`**

```sql
-- 知识管理系统 建表脚本
-- 注意：中文全文检索必须使用 ngram 解析器，且三列各建独立索引（见设计文档 §5.2）

CREATE DATABASE IF NOT EXISTS kms
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE kms;

DROP TABLE IF EXISTS item_tag;
DROP TABLE IF EXISTS attachment;
DROP TABLE IF EXISTS knowledge_item;
DROP TABLE IF EXISTS tag;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS `user`;

-- 用户（security 包拥有）
CREATE TABLE `user` (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(50)           DEFAULT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status        TINYINT      NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户';

-- 分类树（knowledge 包拥有，邻接表）
CREATE TABLE category (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)  NOT NULL,
    parent_id   BIGINT       NOT NULL DEFAULT 0,
    level       INT          NOT NULL DEFAULT 1,
    sort        INT          NOT NULL DEFAULT 0,
    description VARCHAR(255)          DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '分类树';

-- 知识条目（knowledge 包拥有）
CREATE TABLE knowledge_item (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    title        VARCHAR(200) NOT NULL,
    summary      VARCHAR(500)          DEFAULT NULL,
    content      LONGTEXT,
    content_type VARCHAR(20)  NOT NULL DEFAULT 'MARKDOWN',
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    category_id  BIGINT                DEFAULT NULL,
    author_id    BIGINT       NOT NULL,
    view_count   INT          NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    published_at DATETIME              DEFAULT NULL,
    archived_at  DATETIME              DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_category (category_id),
    KEY idx_status (status),
    KEY idx_author (author_id),
    FULLTEXT KEY ft_title   (title)   WITH PARSER ngram,
    FULLTEXT KEY ft_summary (summary) WITH PARSER ngram,
    FULLTEXT KEY ft_content (content) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '知识条目';

-- 标签（knowledge 包拥有）
CREATE TABLE tag (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '标签';

-- 条目-标签关联（knowledge 包拥有）
CREATE TABLE item_tag (
    item_id BIGINT NOT NULL,
    tag_id  BIGINT NOT NULL,
    PRIMARY KEY (item_id, tag_id),
    KEY idx_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '条目标签关联';

-- 附件（ingestion 包拥有）
CREATE TABLE attachment (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    item_id       BIGINT       NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_path   VARCHAR(500) NOT NULL,
    mime_type     VARCHAR(100)          DEFAULT NULL,
    size_bytes    BIGINT       NOT NULL DEFAULT 0,
    parsed_text   LONGTEXT,
    parse_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    parse_error   VARCHAR(500)          DEFAULT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_item (item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '附件';
```

- [ ] **Step 2: 创建 `sql/seed.sql`（用户与分类）**

密码明文：`admin123` → 管理员；`user123` → 普通用户。
下方哈希值由 BCrypt 生成，实现时可重新生成（见 Step 3）。

```sql
USE kms;

DELETE FROM item_tag;
DELETE FROM attachment;
DELETE FROM knowledge_item;
DELETE FROM tag;
DELETE FROM category;
DELETE FROM `user`;

-- 密码哈希占位：Step 3 用真实 BCrypt 值替换
INSERT INTO `user` (id, username, password_hash, nickname, role, status) VALUES
  (1, 'admin', '{BCRYPT_ADMIN123}', '管理员', 'ADMIN', 1),
  (2, 'zhangsan', '{BCRYPT_USER123}', '张三', 'USER', 1);

-- 分类树：3 级层次
INSERT INTO category (id, name, parent_id, level, sort, description) VALUES
  (1, '编程语言', 0, 1, 1, '编程语言相关知识'),
  (2, 'Java',     1, 2, 1, 'Java 语言'),
  (3, 'Python',   1, 2, 2, 'Python 语言'),
  (4, 'Spring',   2, 3, 1, 'Spring 生态'),
  (5, 'JVM',      2, 3, 2, 'Java 虚拟机'),
  (10, '数据库',   0, 1, 2, '数据库相关知识'),
  (11, 'MySQL',   10, 2, 1, 'MySQL 数据库'),
  (12, 'Redis',   10, 2, 2, 'Redis 缓存'),
  (20, '软件工程', 0, 1, 3, '软件工程相关知识'),
  (21, '需求分析', 20, 2, 1, '需求分析方法'),
  (22, '软件测试', 20, 2, 2, '软件测试方法');

INSERT INTO tag (id, name) VALUES
  (1, '入门'), (2, '进阶'), (3, '面试'), (4, '实战'), (5, '笔记');
```

- [ ] **Step 3: 生成真实 BCrypt 哈希并替换占位符**

创建一个临时测试类生成哈希（跑完即删）：

```java
package com.njupt.kms;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordHashGenerator {
    @Test
    void generate() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println("admin123 -> " + encoder.encode("admin123"));
        System.out.println("user123  -> " + encoder.encode("user123"));
    }
}
```

```bash
mvn -q test -Dtest=PasswordHashGenerator
```

把输出的两个哈希分别替换 `seed.sql` 中的 `{BCRYPT_ADMIN123}` 和 `{BCRYPT_USER123}`，然后删除该临时测试类。

- [ ] **Step 4: 创建 `sql/seed.sql` 的条目数据部分（追加到文件末尾）**

```sql
-- 知识条目：20 条，覆盖三种状态与多个分类
INSERT INTO knowledge_item (id, title, summary, content, content_type, status, category_id, author_id, view_count, published_at) VALUES
  (1,  'Java 基础语法速查', '变量、运算符、流程控制的要点整理', '# Java 基础语法\n\n变量声明、运算符优先级、if/for/while 的写法与常见陷阱。', 'MARKDOWN', 'PUBLISHED', 2, 1, 120, NOW()),
  (2,  'Java 集合框架详解', 'List、Set、Map 的实现与选型', '# 集合框架\n\nArrayList 与 LinkedList 的差异、HashMap 的扩容机制。', 'MARKDOWN', 'PUBLISHED', 2, 1, 98, NOW()),
  (3,  'Spring IoC 容器原理', 'Bean 的生命周期与依赖注入', '# Spring IoC\n\nBeanDefinition 加载、实例化、属性填充、初始化。', 'MARKDOWN', 'PUBLISHED', 4, 1, 210, NOW()),
  (4,  'Spring AOP 与动态代理', 'JDK 动态代理与 CGLIB 的区别', '# Spring AOP\n\n切面、切点、通知的概念与实现方式。', 'MARKDOWN', 'PUBLISHED', 4, 2, 156, NOW()),
  (5,  'JVM 内存结构', '堆、栈、方法区与垃圾回收', '# JVM 内存\n\n新生代与老年代的划分，常见 GC 算法。', 'MARKDOWN', 'PUBLISHED', 5, 1, 305, NOW()),
  (6,  'JVM 调优实战记录', '线上 OOM 排查过程', '# 调优记录\n\n通过 jmap 与 MAT 定位内存泄漏。', 'MARKDOWN', 'DRAFT', 5, 1, 0, NULL),
  (7,  'Python 列表推导式', '优雅的循环写法', '# 列表推导式\n\n[x*2 for x in range(10)] 及其性能考量。', 'MARKDOWN', 'PUBLISHED', 3, 2, 77, NOW()),
  (8,  'Python 装饰器入门', '函数式编程的基础工具', '# 装饰器\n\n闭包、函数作为参数、functools.wraps。', 'MARKDOWN', 'PUBLISHED', 3, 2, 65, NOW()),
  (9,  'MySQL 索引原理', 'B+ 树与最左前缀原则', '# 索引原理\n\n聚簇索引与非聚簇索引，回表查询的代价。', 'MARKDOWN', 'PUBLISHED', 11, 1, 402, NOW()),
  (10, 'MySQL 慢查询优化', 'EXPLAIN 的使用方法', '# 慢查询\n\n通过 EXPLAIN 分析执行计划并建立合适索引。', 'MARKDOWN', 'PUBLISHED', 11, 1, 288, NOW()),
  (11, 'MySQL 事务隔离级别', '四种隔离级别与幻读问题', '# 事务\n\n读未提交、读已提交、可重复读、串行化。', 'MARKDOWN', 'PUBLISHED', 11, 2, 190, NOW()),
  (12, 'Redis 数据结构与应用', 'String、Hash、List、Set、ZSet', '# Redis 数据结构\n\n五种基础类型的使用场景。', 'MARKDOWN', 'PUBLISHED', 12, 1, 233, NOW()),
  (13, 'Redis 缓存穿透与雪崩', '常见缓存问题及解决方案', '# 缓存问题\n\n空值缓存、布隆过滤器、过期时间打散。', 'MARKDOWN', 'PUBLISHED', 12, 1, 175, NOW()),
  (14, 'RAG 检索增强生成入门', '大模型与知识库结合的基本思路', '# RAG\n\n文档切块、向量化、相似度检索、上下文注入。', 'MARKDOWN', 'ARCHIVED', 1, 1, 88, NOW()),
  (15, '需求分析方法论', '用例图与用例描述', '# 需求分析\n\n识别参与者、梳理用例、编写用例规约。', 'MARKDOWN', 'PUBLISHED', 21, 2, 143, NOW()),
  (16, '软件测试用例设计', '等价类划分与边界值分析', '# 测试用例\n\n黑盒测试方法，正常与异常路径覆盖。', 'MARKDOWN', 'PUBLISHED', 22, 2, 167, NOW()),
  (17, '单元测试与 Mock 实践', 'JUnit 5 与 Mockito 的使用', '# 单元测试\n\n测试隔离、依赖替身、断言库选型。', 'MARKDOWN', 'PUBLISHED', 22, 1, 121, NOW()),
  (18, 'ER 图设计要点', '实体识别与关系基数', '# ER 图\n\n一对一、一对多、多对多的画法与落地。', 'MARKDOWN', 'PUBLISHED', 21, 2, 99, NOW()),
  (19, '软件架构风格对比', '分层、微内核、事件驱动', '# 架构风格\n\n各风格的适用场景与代价。', 'MARKDOWN', 'PUBLISHED', 20, 1, 211, NOW()),
  (20, '课程设计答辩准备清单', '功能演示与结构描述的要点', '# 答辩准备\n\n演示脚本、结构讲解、测试数据、问题记录。', 'MARKDOWN', 'DRAFT', 20, 1, 0, NULL);

INSERT INTO item_tag (item_id, tag_id) VALUES
  (1,1),(1,5),(2,2),(2,3),(3,2),(3,4),(4,2),(4,3),(5,2),(5,3),
  (7,1),(8,1),(9,2),(9,4),(10,2),(10,4),(11,3),(12,2),(12,4),(13,2),
  (14,2),(15,1),(16,1),(16,5),(17,4),(18,1),(19,2),(20,5);
```

- [ ] **Step 5: 执行脚本验证**

```bash
mysql -u root -p < kms-backend/sql/schema.sql
mysql -u root -p < kms-backend/sql/seed.sql
mysql -u root -p -e "USE kms; SHOW TABLES; SELECT COUNT(*) AS items FROM knowledge_item; SELECT id,title FROM knowledge_item WHERE MATCH(title) AGAINST('Java' IN NATURAL LANGUAGE MODE);"
```

Expected:
- 列出 6 张表（`user` `category` `knowledge_item` `tag` `item_tag` `attachment`）
- `items` = 20
- 全文检索返回标题含 "Java" 的行（证明 ngram 生效）

**若全文检索返回空**：说明 ngram 索引未生效。检查是否 MySQL 8（`SELECT VERSION()`），以及 `SHOW INDEX FROM knowledge_item` 中三个 `ft_*` 索引的 `Index_type` 是否为 `FULLTEXT`。

- [ ] **Step 6: 提交**

```bash
git add kms-backend/sql
git commit -m "feat(db): add schema with ngram fulltext indexes and seed data"
```

---

### Task 3: M0 统一响应与全局异常

**Files:**
- Create: `kms-backend/src/main/java/com/njupt/kms/common/Result.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/common/ResultCode.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/common/BusinessException.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/common/GlobalExceptionHandler.java`
- Test: `kms-backend/src/test/java/com/njupt/kms/common/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `Result<T>`：静态工厂 `ok()` / `ok(T data)` / `fail(int code, String message)`；字段 `code` `message` `data`
  - `ResultCode`：常量 `SUCCESS=0` `PARAM_ERROR=1001` `UNAUTHORIZED=1002` `FORBIDDEN=1003` `NOT_FOUND=1004` `BUSINESS_ERROR=2000` `SYSTEM_ERROR=5000`
  - `BusinessException(String message)` / `BusinessException(int code, String message)` / `int getCode()`

- [ ] **Step 1: 写失败测试 `GlobalExceptionHandlerTest.java`**

> **为什么不用 `@WebMvcTest`**：本项目存在 `WebConfig implements WebMvcConfigurer`，
> 而 `@WebMvcTest` 的扫描范围**包含** `WebMvcConfigurer`、**排除** `@Component`。
> 因此 `WebConfig` 会被加载，但它依赖的 `AuthInterceptor`（`@Component`）不会被创建，
> 上下文启动即失败。standalone 模式完全绕开 Spring 容器，且测试更快。

```java
package com.njupt.kms.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {
        @GetMapping("/probe/business")
        Result<Void> business() {
            throw new BusinessException("分类不存在");
        }

        @GetMapping("/probe/system")
        Result<Void> system() {
            throw new IllegalStateException("Cannot get connection: jdbc:mysql://localhost:3306/kms, root/secret123");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void business_exception_returns_code_and_message() throws Exception {
        mockMvc.perform(get("/probe/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.BUSINESS_ERROR))
                .andExpect(jsonPath("$.message").value("分类不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void system_exception_hides_internal_detail() throws Exception {
        mockMvc.perform(get("/probe/system"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ResultCode.SYSTEM_ERROR))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd kms-backend && mvn -q test -Dtest=GlobalExceptionHandlerTest`
Expected: 编译失败 —— `Result`、`ResultCode`、`BusinessException`、`GlobalExceptionHandler` 尚不存在

- [ ] **Step 3: 创建 `ResultCode.java`**

```java
package com.njupt.kms.common;

/** 统一响应码。0 表示成功，其余为业务失败。 */
public final class ResultCode {

    private ResultCode() {
    }

    public static final int SUCCESS = 0;
    /** 参数校验失败 */
    public static final int PARAM_ERROR = 1001;
    /** 未登录或登录已过期 */
    public static final int UNAUTHORIZED = 1002;
    /** 已登录但无权操作 */
    public static final int FORBIDDEN = 1003;
    /** 资源不存在 */
    public static final int NOT_FOUND = 1004;
    /** 一般业务失败 */
    public static final int BUSINESS_ERROR = 2000;
    /** 系统异常（原因不外泄） */
    public static final int SYSTEM_ERROR = 5000;
}
```

- [ ] **Step 4: 创建 `Result.java`**

```java
package com.njupt.kms.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/** 统一响应体。所有接口返回此结构，不裸返实体。 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private final int code;
    private final String message;
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS, "ok", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS, "ok", data);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
```

`@JsonInclude(NON_NULL)` 保证 `data` 为 null 时不序列化，满足测试第二、三条断言。

- [ ] **Step 5: 创建 `BusinessException.java`**

```java
package com.njupt.kms.common;

import lombok.Getter;

/** 业务失败。message 会原样展示给用户，必须写成人话。 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(ResultCode.BUSINESS_ERROR, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

- [ ] **Step 6: 创建 `GlobalExceptionHandler.java`**

```java
package com.njupt.kms.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务失败：HTTP 200 + 非零 code，message 可直接展示 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：拼出字段级提示 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        if (message.isBlank()) {
            message = "参数校验失败";
        }
        log.warn("参数校验失败: {}", message);
        return Result.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 系统异常：完整堆栈只进日志，绝不回传前端。
     * 防止数据库连接串、密钥等内部信息通过错误信息泄露。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleSystem(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.SYSTEM_ERROR, "系统繁忙，请稍后重试");
    }
}
```

- [ ] **Step 7: 运行测试确认通过**

Run: `cd kms-backend && mvn -q test -Dtest=GlobalExceptionHandlerTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 8: 提交**

```bash
git add kms-backend/src/main/java/com/njupt/kms/common kms-backend/src/test/java/com/njupt/kms/common
git commit -m "feat(common): add unified response body and global exception handler"
```

---

### Task 4: M0 认证（登录 / 登出 / 拦截器）

**Files:**
- Create: `kms-backend/src/main/java/com/njupt/kms/security/User.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/security/UserMapper.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/security/CurrentUser.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/security/AuthService.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/security/AuthInterceptor.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/security/AuthController.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/security/dto/LoginRequest.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/security/dto/UserVO.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/config/WebConfig.java`
- Test: `kms-backend/src/test/java/com/njupt/kms/security/AuthServiceTest.java`

**Interfaces:**
- Consumes: `Result`、`ResultCode`、`BusinessException`（Task 3）
- Produces:
  - `CurrentUser.get()` → `User`（当前登录用户，未登录返回 `null`）；`CurrentUser.getRequired()` → `User`（未登录抛 `BusinessException(UNAUTHORIZED, ...)`）；`CurrentUser.set(User)` / `CurrentUser.clear()`
  - `AuthService.login(String username, String rawPassword)` → `String token`；`logout(String token)`；`resolve(String token)` → `User` 或 `null`
  - `User` 字段：`id` `username` `passwordHash` `nickname` `role` `status`；`boolean isAdmin()`

- [ ] **Step 1: 写失败测试 `AuthServiceTest.java`**

```java
package com.njupt.kms.security;

import com.njupt.kms.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserMapper userMapper;
    private TokenStore tokenStore;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        tokenStore = mock(TokenStore.class);
        authService = new AuthService(userMapper, tokenStore, new BCryptPasswordEncoder());
    }

    private User user(String rawPassword) {
        User u = new User();
        u.setId(2L);
        u.setUsername("zhangsan");
        u.setPasswordHash(new BCryptPasswordEncoder().encode(rawPassword));
        u.setRole("USER");
        u.setStatus(1);
        return u;
    }

    @Test
    void login_returns_token_on_correct_password() {
        when(userMapper.selectOne(any())).thenReturn(user("user123"));

        String token = authService.login("zhangsan", "user123");

        assertThat(token).isNotBlank();
        verify(tokenStore).save(eq(token), eq(2L));
    }

    @Test
    void login_rejects_wrong_password() {
        when(userMapper.selectOne(any())).thenReturn(user("user123"));

        assertThatThrownBy(() -> authService.login("zhangsan", "wrong"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void login_rejects_unknown_user_with_same_message() {
        when(userMapper.selectOne(any())).thenReturn(null);

        // 不区分"用户不存在"与"密码错误"，避免用户名枚举
        assertThatThrownBy(() -> authService.login("nobody", "whatever"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void login_rejects_disabled_user() {
        User disabled = user("user123");
        disabled.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(disabled);

        assertThatThrownBy(() -> authService.login("zhangsan", "user123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号已被禁用");
    }

    @Test
    void resolve_returns_null_for_blank_token() {
        assertThat(authService.resolve(null)).isNull();
        assertThat(authService.resolve("")).isNull();
    }

    @Test
    void resolve_returns_user_for_valid_token() {
        when(tokenStore.findUserId("tok")).thenReturn(2L);
        when(userMapper.selectById(2L)).thenReturn(user("user123"));

        assertThat(authService.resolve("tok")).isNotNull();
        assertThat(authService.resolve("tok").getUsername()).isEqualTo("zhangsan");
    }

    @Test
    void logout_removes_token() {
        authService.logout("tok");
        verify(tokenStore).remove("tok");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd kms-backend && mvn -q test -Dtest=AuthServiceTest`
Expected: 编译失败 —— `User`、`UserMapper`、`TokenStore`、`AuthService` 尚不存在

- [ ] **Step 3: 创建 `User.java`**

```java
package com.njupt.kms.security;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String nickname;

    private String role;

    private Integer status;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
```

- [ ] **Step 4: 创建 `UserMapper.java`**

```java
package com.njupt.kms.security;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

- [ ] **Step 5: 创建 `TokenStore.java`（Redis 令牌存储）**

```java
package com.njupt.kms.security;

/** 令牌存储抽象。当前实现为 Redis，便于后续替换。 */
public interface TokenStore {

    void save(String token, Long userId);

    Long findUserId(String token);

    void remove(String token);
}
```

- [ ] **Step 6: 创建 `RedisTokenStore.java`**

```java
package com.njupt.kms.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisTokenStore implements TokenStore {

    private static final String KEY_PREFIX = "kms:token:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String token, Long userId) {
        redisTemplate.opsForValue().set(KEY_PREFIX + token, String.valueOf(userId), TTL);
    }

    @Override
    public Long findUserId(String token) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        return value == null ? null : Long.valueOf(value);
    }

    @Override
    public void remove(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }
}
```

- [ ] **Step 7: 创建 `AuthService.java`**

```java
package com.njupt.kms.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.njupt.kms.common.BusinessException;
import com.njupt.kms.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final TokenStore tokenStore;
    private final PasswordEncoder passwordEncoder;

    public String login(String username, String rawPassword) {
        User user = StringUtils.hasText(username)
                ? userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username))
                : null;

        // 用户不存在与密码错误返回同一提示，避免用户名枚举
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        tokenStore.save(token, user.getId());
        return token;
    }

    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            tokenStore.remove(token);
        }
    }

    /** 令牌无效或用户已被删除/禁用时返回 null。 */
    public User resolve(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        Long userId = tokenStore.findUserId(token);
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            return null;
        }
        return user;
    }

    public User requireUser(String token) {
        User user = resolve(token);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return user;
    }
}
```

- [ ] **Step 8: 创建 `PasswordEncoderConfig.java`**

`PasswordEncoder` 需要一个 bean。注意：**不要**引入 `spring-boot-starter-security`，否则整条安全过滤器链会启用。

```java
package com.njupt.kms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 9: 创建 `CurrentUser.java`**

```java
package com.njupt.kms.security;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.common.ResultCode;

/** 当前请求的登录用户上下文。由 AuthInterceptor 写入与清理。 */
public final class CurrentUser {

    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static void set(User user) {
        HOLDER.set(user);
    }

    /** 未登录返回 null。 */
    public static User get() {
        return HOLDER.get();
    }

    /** 未登录抛异常。业务方法应使用此方法强制登录。 */
    public static User getRequired() {
        User user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return user;
    }

    public static Long getId() {
        return getRequired().getId();
    }

    /** 必须在请求结束时调用，否则线程池复用会造成身份串号。 */
    public static void clear() {
        HOLDER.remove();
    }
}
```

- [ ] **Step 10: 创建 `AuthInterceptor.java`**

```java
package com.njupt.kms.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "Authorization";

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader(TOKEN_HEADER);
        if (StringUtils.hasText(token)) {
            CurrentUser.set(authService.resolve(token));
        }
        // 始终放行：是否强制登录由业务方法经 CurrentUser.getRequired() 决定。
        // 这样公开接口（如浏览已发布条目）无需登录，写操作自然被拒。
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CurrentUser.clear();
    }
}
```

- [ ] **Step 11: 创建 `WebConfig.java`**

```java
package com.njupt.kms.config;

import com.njupt.kms.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

- [ ] **Step 12: 创建 DTO `LoginRequest.java` 与 `UserVO.java`**

```java
package com.njupt.kms.security.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
```

```java
package com.njupt.kms.security.dto;

import com.njupt.kms.security.User;
import lombok.Data;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String role;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        return vo;
    }
}
```

- [ ] **Step 13: 创建 `AuthController.java`**

```java
package com.njupt.kms.security;

import com.njupt.kms.common.Result;
import com.njupt.kms.security.dto.LoginRequest;
import com.njupt.kms.security.dto.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.getUsername(), request.getPassword());
        User user = authService.requireUser(token);
        return Result.ok(Map.of("token", token, "user", UserVO.from(user)));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = AuthInterceptor.TOKEN_HEADER, required = false) String token) {
        authService.logout(token);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(UserVO.from(CurrentUser.getRequired()));
    }
}
```

- [ ] **Step 14: 运行测试确认通过**

Run: `cd kms-backend && mvn -q test -Dtest=AuthServiceTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 15: 手工验证端到端**

```bash
cd kms-backend && mvn -q spring-boot:run
# 另开终端
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

Expected: `{"code":0,"message":"ok","data":{"token":"...","user":{...}}}`

```bash
curl -s http://localhost:8080/api/auth/me
```
Expected: `{"code":1002,"message":"请先登录"}`

- [ ] **Step 16: 提交**

```bash
git add kms-backend/src
git commit -m "feat(auth): add token login, logout and request-scoped user context"
```

---

### Task 5: M1 分类树构建算法

**Files:**
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/entity/Category.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/dto/CategoryNode.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/service/CategoryTreeBuilder.java`
- Test: `kms-backend/src/test/java/com/njupt/kms/knowledge/CategoryTreeBuilderTest.java`

**Interfaces:**
- Consumes: 无（纯函数，不依赖 Spring）
- Produces:
  - `Category` 字段：`id` `name` `parentId` `level` `sort` `description` `createdAt` `updatedAt`
  - `CategoryNode` 字段：`id` `name` `parentId` `level` `sort` `description` `children`（`List<CategoryNode>`）
  - `CategoryTreeBuilder.build(List<Category> flat)` → `List<CategoryNode>`
  - `CategoryTreeBuilder.collectDescendantIds(List<Category> flat, Long rootId)` → `Set<Long>`（含自身）

- [ ] **Step 1: 写失败测试 `CategoryTreeBuilderTest.java`**

```java
package com.njupt.kms.knowledge;

import com.njupt.kms.knowledge.dto.CategoryNode;
import com.njupt.kms.knowledge.entity.Category;
import com.njupt.kms.knowledge.service.CategoryTreeBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTreeBuilderTest {

    private Category cat(Long id, Long parentId, String name, int sort) {
        Category c = new Category();
        c.setId(id);
        c.setParentId(parentId);
        c.setName(name);
        c.setSort(sort);
        return c;
    }

    @Test
    void empty_input_yields_empty_tree() {
        assertThat(CategoryTreeBuilder.build(List.of())).isEmpty();
    }

    @Test
    void builds_three_level_tree() {
        List<Category> flat = List.of(
                cat(1L, 0L, "编程语言", 1),
                cat(2L, 1L, "Java", 1),
                cat(3L, 1L, "Python", 2),
                cat(4L, 2L, "Spring", 1),
                cat(5L, 2L, "JVM", 2)
        );

        List<CategoryNode> tree = CategoryTreeBuilder.build(flat);

        assertThat(tree).hasSize(1);
        CategoryNode root = tree.get(0);
        assertThat(root.getName()).isEqualTo("编程语言");
        assertThat(root.getChildren()).extracting(CategoryNode::getName)
                .containsExactly("Java", "Python");
        assertThat(root.getChildren().get(0).getChildren())
                .extracting(CategoryNode::getName)
                .containsExactly("Spring", "JVM");
        assertThat(root.getChildren().get(1).getChildren()).isEmpty();
    }

    @Test
    void sorts_siblings_by_sort_field_regardless_of_input_order() {
        List<Category> flat = List.of(
                cat(3L, 1L, "Python", 2),
                cat(1L, 0L, "编程语言", 1),
                cat(2L, 1L, "Java", 1)
        );

        List<CategoryNode> tree = CategoryTreeBuilder.build(flat);

        assertThat(tree.get(0).getChildren()).extracting(CategoryNode::getName)
                .containsExactly("Java", "Python");
    }

    @Test
    void supports_multiple_roots() {
        List<Category> flat = List.of(
                cat(10L, 0L, "数据库", 2),
                cat(1L, 0L, "编程语言", 1),
                cat(20L, 0L, "软件工程", 3)
        );

        assertThat(CategoryTreeBuilder.build(flat)).extracting(CategoryNode::getName)
                .containsExactly("编程语言", "数据库", "软件工程");
    }

    @Test
    void orphan_node_falls_back_to_root_instead_of_being_lost() {
        // parentId=999 不存在。选择降级为根节点而非丢弃，避免数据静默丢失。
        List<Category> flat = List.of(
                cat(1L, 0L, "编程语言", 1),
                cat(9L, 999L, "孤儿分类", 1)
        );

        assertThat(CategoryTreeBuilder.build(flat)).extracting(CategoryNode::getName)
                .containsExactly("编程语言", "孤儿分类");
    }

    @Test
    void collect_descendant_ids_includes_self_and_all_levels() {
        List<Category> flat = List.of(
                cat(1L, 0L, "编程语言", 1),
                cat(2L, 1L, "Java", 1),
                cat(3L, 1L, "Python", 2),
                cat(4L, 2L, "Spring", 1)
        );

        assertThat(CategoryTreeBuilder.collectDescendantIds(flat, 1L))
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        assertThat(CategoryTreeBuilder.collectDescendantIds(flat, 2L))
                .containsExactlyInAnyOrder(2L, 4L);
        assertThat(CategoryTreeBuilder.collectDescendantIds(flat, 3L))
                .containsExactly(3L);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd kms-backend && mvn -q test -Dtest=CategoryTreeBuilderTest`
Expected: 编译失败 —— `Category`、`CategoryNode`、`CategoryTreeBuilder` 尚不存在

- [ ] **Step 3: 创建 `Category.java`**

```java
package com.njupt.kms.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 0 表示根节点 */
    private Long parentId;

    /** 层级，从 1 起 */
    private Integer level;

    private Integer sort;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: 创建 `CategoryNode.java`**

```java
package com.njupt.kms.knowledge.dto;

import com.njupt.kms.knowledge.entity.Category;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryNode {

    private Long id;
    private String name;
    private Long parentId;
    private Integer level;
    private Integer sort;
    private String description;
    private List<CategoryNode> children = new ArrayList<>();

    public static CategoryNode from(Category category) {
        CategoryNode node = new CategoryNode();
        node.setId(category.getId());
        node.setName(category.getName());
        node.setParentId(category.getParentId());
        node.setLevel(category.getLevel());
        node.setSort(category.getSort());
        node.setDescription(category.getDescription());
        return node;
    }
}
```

- [ ] **Step 5: 创建 `CategoryTreeBuilder.java`**

```java
package com.njupt.kms.knowledge.service;

import com.njupt.kms.knowledge.dto.CategoryNode;
import com.njupt.kms.knowledge.entity.Category;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分类树构建：邻接表（parent_id）→ 树形结构。
 *
 * <p>算法为「一次全表查询 + 内存组装」，时间复杂度 O(n log n)（排序主导），
 * 优于按层递归查库的 O(n × depth) 次数据库往返。
 *
 * <p>纯函数，不依赖 Spring 容器，便于独立测试。
 */
public final class CategoryTreeBuilder {

    private static final Comparator<CategoryNode> BY_SORT_THEN_ID =
            Comparator.comparing(CategoryNode::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(CategoryNode::getId);

    private CategoryTreeBuilder() {
    }

    /**
     * 把扁平的分类列表组装成树。
     *
     * <p>parentId 指向不存在的分类时，该节点降级为根节点（而非丢弃），
     * 避免数据异常导致分类静默消失、用户无从发现。
     */
    public static List<CategoryNode> build(List<Category> flat) {
        if (flat == null || flat.isEmpty()) {
            return new ArrayList<>();
        }

        // 第一遍：建 id → node 映射
        Map<Long, CategoryNode> nodeMap = new LinkedHashMap<>();
        for (Category category : flat) {
            nodeMap.put(category.getId(), CategoryNode.from(category));
        }

        // 第二遍：挂接父子关系
        List<CategoryNode> roots = new ArrayList<>();
        for (CategoryNode node : nodeMap.values()) {
            Long parentId = node.getParentId();
            CategoryNode parent = (parentId == null || parentId == 0L) ? null : nodeMap.get(parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }

        sortRecursively(roots);
        return roots;
    }

    /**
     * 求某分类的全部后代 id（含自身）。用于「点击父分类时显示下级全部条目」。
     *
     * <p>使用显式栈而非递归，避免分类层级过深时栈溢出。
     */
    public static Set<Long> collectDescendantIds(List<Category> flat, Long rootId) {
        Set<Long> result = new HashSet<>();
        if (flat == null || flat.isEmpty() || rootId == null) {
            return result;
        }

        Map<Long, List<Long>> childrenOf = new HashMap<>();
        for (Category category : flat) {
            childrenOf.computeIfAbsent(category.getParentId(), k -> new ArrayList<>())
                    .add(category.getId());
        }

        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            if (!result.add(current)) {
                continue;   // 已访问，防止脏数据成环导致死循环
            }
            for (Long child : childrenOf.getOrDefault(current, List.of())) {
                stack.push(child);
            }
        }
        return result;
    }

    private static void sortRecursively(List<CategoryNode> nodes) {
        nodes.sort(BY_SORT_THEN_ID);
        for (CategoryNode node : nodes) {
            sortRecursively(node.getChildren());
        }
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `cd kms-backend && mvn -q test -Dtest=CategoryTreeBuilderTest`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 7: 提交**

```bash
git add kms-backend/src/main/java/com/njupt/kms/knowledge kms-backend/src/test/java/com/njupt/kms/knowledge
git commit -m "feat(knowledge): add adjacency-list category tree builder with descendant lookup"
```

---

### Task 6: M1 分类 CRUD 与层级维护

**Files:**
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/mapper/CategoryMapper.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/mapper/KnowledgeItemMapper.java`（本任务先建最小版本，Task 7 补全）
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/service/CategoryService.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/service/impl/CategoryServiceImpl.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/controller/CategoryController.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/dto/CategorySaveRequest.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/config/MybatisPlusConfig.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/config/MetaObjectFillHandler.java`
- Test: `kms-backend/src/test/java/com/njupt/kms/knowledge/CategoryServiceTest.java`

**Interfaces:**
- Consumes: `Category`、`CategoryNode`、`CategoryTreeBuilder`（Task 5）；`BusinessException`、`CurrentUser`（Task 3/4）
- Produces:
  - `CategoryService.tree()` → `List<CategoryNode>`
  - `CategoryService.create(CategorySaveRequest)` → `Category`
  - `CategoryService.update(Long id, CategorySaveRequest)` → `Category`
  - `CategoryService.delete(Long id)`
  - `CategoryService.descendantIds(Long categoryId)` → `Set<Long>`
  - `CategoryService.all()` → `List<Category>`
  - `CategorySaveRequest` 字段：`name`（必填）`parentId` `sort` `description`

- [ ] **Step 1: 写失败测试 `CategoryServiceTest.java`**

```java
package com.njupt.kms.knowledge;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.knowledge.dto.CategoryNode;
import com.njupt.kms.knowledge.dto.CategorySaveRequest;
import com.njupt.kms.knowledge.entity.Category;
import com.njupt.kms.knowledge.mapper.CategoryMapper;
import com.njupt.kms.knowledge.mapper.KnowledgeItemMapper;
import com.njupt.kms.knowledge.service.CategoryService;
import com.njupt.kms.knowledge.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    private CategoryMapper categoryMapper;
    private KnowledgeItemMapper knowledgeItemMapper;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryMapper = mock(CategoryMapper.class);
        knowledgeItemMapper = mock(KnowledgeItemMapper.class);
        categoryService = new CategoryServiceImpl(categoryMapper, knowledgeItemMapper);
    }

    private Category cat(Long id, Long parentId, int level, String name) {
        Category c = new Category();
        c.setId(id);
        c.setParentId(parentId);
        c.setLevel(level);
        c.setName(name);
        c.setSort(0);
        return c;
    }

    private CategorySaveRequest req(String name, Long parentId) {
        CategorySaveRequest r = new CategorySaveRequest();
        r.setName(name);
        r.setParentId(parentId);
        r.setSort(0);
        return r;
    }

    @Test
    void create_at_root_sets_level_one() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        Category created = categoryService.create(req("编程语言", 0L));

        assertThat(created.getLevel()).isEqualTo(1);
    }

    @Test
    void create_under_parent_sets_level_parent_plus_one() {
        when(categoryMapper.selectById(2L)).thenReturn(cat(2L, 0L, 1, "编程语言"));
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        Category created = categoryService.create(req("Java", 2L));

        assertThat(created.getLevel()).isEqualTo(2);
        assertThat(created.getParentId()).isEqualTo(2L);
    }

    @Test
    void create_rejects_missing_parent() {
        when(categoryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> categoryService.create(req("Java", 999L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("父分类不存在");
    }

    @Test
    void create_rejects_duplicate_name_under_same_parent() {
        when(categoryMapper.selectById(2L)).thenReturn(cat(2L, 0L, 1, "编程语言"));
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> categoryService.create(req("Java", 2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同级下已存在同名分类");
    }

    @Test
    void update_rejects_missing_category() {
        when(categoryMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> categoryService.update(99L, req("新名字", 0L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分类不存在");
    }

    @Test
    void update_ignores_parent_change() {
        // v0.0.1 不支持移动分类：改父节点需重算整棵子树的 level，
        // 收益低而回归风险高。parentId 变更被静默忽略。
        Category existing = cat(3L, 2L, 2, "Java");
        when(categoryMapper.selectById(3L)).thenReturn(existing);
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        Category updated = categoryService.update(3L, req("Java 语言", 0L));

        assertThat(updated.getLevel()).isEqualTo(2);
        assertThat(updated.getParentId()).isEqualTo(2L);
    }

    @Test
    void delete_rejects_category_with_children() {
        when(categoryMapper.selectById(2L)).thenReturn(cat(2L, 0L, 1, "编程语言"));
        when(categoryMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> categoryService.delete(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("子分类");
    }

    @Test
    void descendant_ids_include_self_and_children() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                cat(1L, 0L, 1, "编程语言"),
                cat(2L, 1L, 2, "Java"),
                cat(3L, 2L, 3, "Spring")
        ));

        assertThat(categoryService.descendantIds(1L)).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void tree_returns_nested_structure() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                cat(1L, 0L, 1, "编程语言"),
                cat(2L, 1L, 2, "Java")
        ));

        List<CategoryNode> tree = categoryService.tree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getChildren()).hasSize(1);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd kms-backend && mvn -q test -Dtest=CategoryServiceTest`
Expected: 编译失败 —— 相关类尚不存在

- [ ] **Step 3: 创建 `CategoryMapper.java` 与最小 `KnowledgeItemMapper.java`**

```java
package com.njupt.kms.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njupt.kms.knowledge.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
```

```java
package com.njupt.kms.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import org.apache.ibatis.annotations.Mapper;

/** Task 7 会扩充自定义查询；本任务只需 selectCount 以校验分类是否被引用。 */
@Mapper
public interface KnowledgeItemMapper extends BaseMapper<KnowledgeItem> {
}
```

- [ ] **Step 4: 创建 `KnowledgeItem.java`（本任务仅需实体定义）**

```java
package com.njupt.kms.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_item")
public class KnowledgeItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String summary;

    private String content;

    private String contentType;

    /** DRAFT / PUBLISHED / ARCHIVED */
    private String status;

    private Long categoryId;

    private Long authorId;

    private Integer viewCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime publishedAt;

    private LocalDateTime archivedAt;
}
```

- [ ] **Step 5: 创建 `CategorySaveRequest.java`**

```java
package com.njupt.kms.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategorySaveRequest {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称不能超过 50 字")
    private String name;

    /** 0 或 null 表示根分类 */
    private Long parentId;

    private Integer sort;

    @Size(max = 255, message = "描述不能超过 255 字")
    private String description;
}
```

- [ ] **Step 6: 创建 `CategoryService.java` 接口**

```java
package com.njupt.kms.knowledge.service;

import com.njupt.kms.knowledge.dto.CategoryNode;
import com.njupt.kms.knowledge.dto.CategorySaveRequest;
import com.njupt.kms.knowledge.entity.Category;

import java.util.List;
import java.util.Set;

public interface CategoryService {

    /** 完整分类树。 */
    List<CategoryNode> tree();

    /** 全部分类的扁平列表，供树算法与后代计算使用。 */
    List<Category> all();

    Category create(CategorySaveRequest request);

    Category update(Long id, CategorySaveRequest request);

    void delete(Long id);

    /** 某分类的全部后代 id（含自身）。 */
    Set<Long> descendantIds(Long categoryId);
}
```

- [ ] **Step 7: 创建 `CategoryServiceImpl.java`**

```java
package com.njupt.kms.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.njupt.kms.common.BusinessException;
import com.njupt.kms.knowledge.dto.CategoryNode;
import com.njupt.kms.knowledge.dto.CategorySaveRequest;
import com.njupt.kms.knowledge.entity.Category;
import com.njupt.kms.knowledge.mapper.CategoryMapper;
import com.njupt.kms.knowledge.mapper.KnowledgeItemMapper;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import com.njupt.kms.knowledge.service.CategoryService;
import com.njupt.kms.knowledge.service.CategoryTreeBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    /** 根分类的 parentId 约定值 */
    private static final long ROOT_PARENT_ID = 0L;

    private final CategoryMapper categoryMapper;
    private final KnowledgeItemMapper knowledgeItemMapper;

    @Override
    public List<CategoryNode> tree() {
        return CategoryTreeBuilder.build(all());
    }

    @Override
    public List<Category> all() {
        return categoryMapper.selectList(
                Wrappers.<Category>lambdaQuery()
                        .orderByAsc(Category::getSort)
                        .orderByAsc(Category::getId));
    }

    @Override
    @Transactional
    public Category create(CategorySaveRequest request) {
        long parentId = request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId();
        int level = resolveLevel(parentId);

        assertNameUniqueUnderParent(request.getName(), parentId, null);

        Category category = new Category();
        category.setName(request.getName().trim());
        category.setParentId(parentId);
        category.setLevel(level);
        category.setSort(request.getSort() == null ? 0 : request.getSort());
        category.setDescription(request.getDescription());
        categoryMapper.insert(category);
        return category;
    }

    @Override
    @Transactional
    public Category update(Long id, CategorySaveRequest request) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }

        // v0.0.1 不支持移动分类：改父节点需重算整棵子树的 level。
        // 请求中的 parentId 被忽略，接口契约与文档一致。
        assertNameUniqueUnderParent(request.getName(), existing.getParentId(), id);

        existing.setName(request.getName().trim());
        existing.setSort(request.getSort() == null ? 0 : request.getSort());
        existing.setDescription(request.getDescription());
        categoryMapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }

        Long childCount = categoryMapper.selectCount(
                Wrappers.<Category>lambdaQuery().eq(Category::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("该分类下有 " + childCount + " 个子分类，请先处理子分类");
        }

        Long itemCount = knowledgeItemMapper.selectCount(
                Wrappers.<KnowledgeItem>lambdaQuery().eq(KnowledgeItem::getCategoryId, id));
        if (itemCount != null && itemCount > 0) {
            throw new BusinessException("该分类下有 " + itemCount + " 个知识条目，无法删除");
        }

        categoryMapper.deleteById(id);
    }

    @Override
    public Set<Long> descendantIds(Long categoryId) {
        if (categoryId == null) {
            return Set.of();
        }
        return CategoryTreeBuilder.collectDescendantIds(all(), categoryId);
    }

    /** parentId → level。父分类必须存在。 */
    private int resolveLevel(long parentId) {
        if (parentId == ROOT_PARENT_ID) {
            return 1;
        }
        Category parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException("父分类不存在");
        }
        return parent.getLevel() + 1;
    }

    /** 同一父节点下不允许重名；excludeId 用于更新时排除自身。 */
    private void assertNameUniqueUnderParent(String name, long parentId, Long excludeId) {
        Long count = categoryMapper.selectCount(
                Wrappers.<Category>lambdaQuery()
                        .eq(Category::getName, name.trim())
                        .eq(Category::getParentId, parentId)
                        .ne(excludeId != null, Category::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("同级下已存在同名分类");
        }
    }
}
```

- [ ] **Step 8: 创建 `MetaObjectFillHandler.java`（自动填充时间戳）**

```java
package com.njupt.kms.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MetaObjectFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
```

- [ ] **Step 9: 创建 `MybatisPlusConfig.java`**

```java
package com.njupt.kms.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **Step 10: 创建 `CategoryController.java`**

```java
package com.njupt.kms.knowledge.controller;

import com.njupt.kms.common.Result;
import com.njupt.kms.knowledge.dto.CategoryNode;
import com.njupt.kms.knowledge.dto.CategorySaveRequest;
import com.njupt.kms.knowledge.entity.Category;
import com.njupt.kms.knowledge.service.CategoryService;
import com.njupt.kms.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** 公开接口：浏览不需要登录。 */
    @GetMapping("/tree")
    public Result<List<CategoryNode>> tree() {
        return Result.ok(categoryService.tree());
    }

    @PostMapping
    public Result<Category> create(@Valid @RequestBody CategorySaveRequest request) {
        CurrentUser.getRequired();
        return Result.ok(categoryService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable Long id, @Valid @RequestBody CategorySaveRequest request) {
        CurrentUser.getRequired();
        return Result.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        CurrentUser.getRequired();
        categoryService.delete(id);
        return Result.ok();
    }
}
```

- [ ] **Step 11: 运行测试确认通过**

Run: `cd kms-backend && mvn -q test -Dtest=CategoryServiceTest`
Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 12: 提交**

```bash
git add kms-backend/src
git commit -m "feat(knowledge): add category CRUD with level maintenance and delete guards"
```

---

### Task 7: M1 知识条目 CRUD

**Files:**
- Modify: `kms-backend/src/main/java/com/njupt/kms/knowledge/mapper/KnowledgeItemMapper.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/service/KnowledgeService.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/service/impl/KnowledgeServiceImpl.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/controller/KnowledgeController.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/dto/KnowledgeSaveRequest.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/dto/KnowledgeDetailVO.java`
- Test: `kms-backend/src/test/java/com/njupt/kms/knowledge/KnowledgeServiceTest.java`

> 列表查询（`page` / `KnowledgeQuery` / `KnowledgeListVO` / 分页 SQL）在 Task 8，
> 因为它与状态可见性过滤是同一个决策，拆开会产生半成品。

**Interfaces:**
- Consumes: `KnowledgeItem`、`CategoryService`（Task 6）、`CurrentUser`、`BusinessException`
- Produces:
  - `KnowledgeService.create(KnowledgeSaveRequest)` → `KnowledgeDetailVO`
  - `KnowledgeService.update(Long id, KnowledgeSaveRequest)` → `KnowledgeDetailVO`
  - `KnowledgeService.detail(Long id)` → `KnowledgeDetailVO`
  - `KnowledgeService.delete(Long id)`
  - `KnowledgeService.getEntity(Long id)` → `KnowledgeItem`（供内部与其他模块使用）
  - `KnowledgeSaveRequest` 字段：`title`（必填）`summary` `content` `contentType` `categoryId` `tagIds`（`List<Long>`）
  - `KnowledgeDetailVO` 字段：`id` `title` `summary` `content` `contentType` `status` `categoryId` `categoryName` `authorId` `authorName` `viewCount` `tags`（`List<String>`）`createdAt` `updatedAt`

- [ ] **Step 1: 写失败测试 `KnowledgeServiceTest.java`**

```java
package com.njupt.kms.knowledge;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.knowledge.dto.KnowledgeDetailVO;
import com.njupt.kms.knowledge.dto.KnowledgeSaveRequest;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import com.njupt.kms.knowledge.mapper.KnowledgeItemMapper;
import com.njupt.kms.knowledge.service.KnowledgeService;
import com.njupt.kms.knowledge.service.TagService;
import com.njupt.kms.knowledge.service.impl.KnowledgeServiceImpl;
import com.njupt.kms.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceTest {

    private KnowledgeItemMapper itemMapper;
    private TagService tagService;
    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        itemMapper = mock(KnowledgeItemMapper.class);
        tagService = mock(TagService.class);
        knowledgeService = new KnowledgeServiceImpl(itemMapper, tagService);
    }

    /** 写测试时把当前用户设为 id=2 的普通用户 */
    private void loginAs(long userId, String role) {
        User user = new User();
        user.setId(userId);
        user.setUsername("u" + userId);
        user.setRole(role);
        user.setStatus(1);
        com.njupt.kms.security.CurrentUser.set(user);
    }

    private KnowledgeItem item(Long id, Long authorId) {
        KnowledgeItem it = new KnowledgeItem();
        it.setId(id);
        it.setTitle("原标题");
        it.setContent("原内容");
        it.setStatus("DRAFT");
        it.setAuthorId(authorId);
        it.setViewCount(0);
        return it;
    }

    private KnowledgeSaveRequest req(String title) {
        KnowledgeSaveRequest r = new KnowledgeSaveRequest();
        r.setTitle(title);
        r.setContent("内容");
        r.setSummary("摘要");
        return r;
    }

    @Test
    void create_defaults_to_draft_and_current_author() {
        loginAs(2L, "USER");
        when(itemMapper.insert(any())).thenAnswer(inv -> {
            ((KnowledgeItem) inv.getArgument(0)).setId(100L);
            return 1;
        });

        KnowledgeDetailVO vo = knowledgeService.create(req("新条目"));

        assertThat(vo.getStatus()).isEqualTo("DRAFT");
        assertThat(vo.getAuthorId()).isEqualTo(2L);
    }

    @Test
    void detail_increments_view_count() {
        loginAs(2L, "USER");
        when(itemMapper.selectById(1L)).thenReturn(item(1L, 2L));

        knowledgeService.detail(1L);

        verify(itemMapper).incrementViewCount(1L);
    }

    @Test
    void detail_rejects_missing_item() {
        loginAs(2L, "USER");
        when(itemMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> knowledgeService.detail(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识条目不存在");
    }

    @Test
    void update_allows_author() {
        loginAs(2L, "USER");
        when(itemMapper.selectById(1L)).thenReturn(item(1L, 2L));

        knowledgeService.update(1L, req("改后标题"));

        verify(itemMapper).updateById(any());
    }

    @Test
    void update_rejects_non_author_non_admin() {
        loginAs(7L, "USER");
        when(itemMapper.selectById(1L)).thenReturn(item(1L, 2L));

        assertThatThrownBy(() -> knowledgeService.update(1L, req("越权改")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权操作他人的知识条目");
    }

    @Test
    void update_allows_admin_on_others_item() {
        loginAs(1L, "ADMIN");
        when(itemMapper.selectById(1L)).thenReturn(item(1L, 2L));

        knowledgeService.update(1L, req("管理员改"));

        verify(itemMapper).updateById(any());
    }

    @Test
    void delete_rejects_non_author_non_admin() {
        loginAs(7L, "USER");
        when(itemMapper.selectById(1L)).thenReturn(item(1L, 2L));

        assertThatThrownBy(() -> knowledgeService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权操作他人的知识条目");
        verify(itemMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void delete_clears_tag_links_first() {
        loginAs(2L, "USER");
        when(itemMapper.selectById(1L)).thenReturn(item(1L, 2L));

        knowledgeService.delete(1L);

        verify(tagService).replaceTags(1L, List.of());
        verify(itemMapper, times(1)).deleteById(1L);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd kms-backend && mvn -q test -Dtest=KnowledgeServiceTest`
Expected: 编译失败 —— 相关类尚不存在

- [ ] **Step 3: 扩充 `KnowledgeItemMapper.java`**

```java
package com.njupt.kms.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeItemMapper extends BaseMapper<KnowledgeItem> {

    /** 浏览数自增。用 SQL 原子自增而非「查-加-写」，避免并发丢失更新。 */
    @Update("UPDATE knowledge_item SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);
}
```

- [ ] **Step 4: 创建 DTO `KnowledgeSaveRequest.java`**

```java
package com.njupt.kms.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeSaveRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    @Size(max = 500, message = "摘要不能超过 500 字")
    private String summary;

    private String content;

    private String contentType;

    private Long categoryId;

    private List<Long> tagIds;
}
```

- [ ] **Step 5: 创建 DTO `KnowledgeDetailVO.java`**

```java
package com.njupt.kms.knowledge.dto;

import com.njupt.kms.knowledge.entity.KnowledgeItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeDetailVO {

    private Long id;
    private String title;
    private String summary;
    private String content;
    private String contentType;
    private String status;
    private Long categoryId;
    private String categoryName;
    private Long authorId;
    private String authorName;
    private Integer viewCount;
    private List<String> tags = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static KnowledgeDetailVO from(KnowledgeItem item) {
        KnowledgeDetailVO vo = new KnowledgeDetailVO();
        vo.setId(item.getId());
        vo.setTitle(item.getTitle());
        vo.setSummary(item.getSummary());
        vo.setContent(item.getContent());
        vo.setContentType(item.getContentType());
        vo.setStatus(item.getStatus());
        vo.setCategoryId(item.getCategoryId());
        vo.setAuthorId(item.getAuthorId());
        vo.setViewCount(item.getViewCount());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }
}
```

- [ ] **Step 6: 创建 `TagService.java`（本任务只需 `replaceTags`，Task 9 补全）**

```java
package com.njupt.kms.knowledge.service;

import java.util.List;

public interface TagService {

    /** 覆盖式替换某条目的标签。传空列表表示清空。 */
    void replaceTags(Long itemId, List<Long> tagIds);
}
```

- [ ] **Step 7: 创建 `KnowledgeService.java` 接口**

```java
package com.njupt.kms.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njupt.kms.knowledge.dto.KnowledgeDetailVO;
import com.njupt.kms.knowledge.dto.KnowledgeListVO;
import com.njupt.kms.knowledge.dto.KnowledgeQuery;
import com.njupt.kms.knowledge.dto.KnowledgeSaveRequest;
import com.njupt.kms.knowledge.entity.KnowledgeItem;

public interface KnowledgeService {

    KnowledgeDetailVO create(KnowledgeSaveRequest request);

    KnowledgeDetailVO update(Long id, KnowledgeSaveRequest request);

    /** 读取详情，并累加浏览数。 */
    KnowledgeDetailVO detail(Long id);

    Page<KnowledgeListVO> page(KnowledgeQuery query);

    void delete(Long id);

    /** 供内部与他模块使用的实体读取，不累加浏览数。 */
    KnowledgeItem getEntity(Long id);
}
```

- [ ] **Step 8: 创建 `KnowledgeServiceImpl.java`**

```java
package com.njupt.kms.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njupt.kms.common.BusinessException;
import com.njupt.kms.knowledge.dto.KnowledgeDetailVO;
import com.njupt.kms.knowledge.dto.KnowledgeListVO;
import com.njupt.kms.knowledge.dto.KnowledgeQuery;
import com.njupt.kms.knowledge.dto.KnowledgeSaveRequest;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import com.njupt.kms.knowledge.mapper.KnowledgeItemMapper;
import com.njupt.kms.knowledge.service.KnowledgeService;
import com.njupt.kms.knowledge.service.TagService;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private final KnowledgeItemMapper itemMapper;
    private final TagService tagService;

    @Override
    @Transactional
    public KnowledgeDetailVO create(KnowledgeSaveRequest request) {
        User current = CurrentUser.getRequired();

        KnowledgeItem item = new KnowledgeItem();
        item.setTitle(request.getTitle().trim());
        item.setSummary(request.getSummary());
        item.setContent(request.getContent());
        item.setContentType(request.getContentType() == null ? "MARKDOWN" : request.getContentType());
        item.setStatus(STATUS_DRAFT);
        item.setCategoryId(request.getCategoryId());
        item.setAuthorId(current.getId());
        item.setViewCount(0);
        itemMapper.insert(item);

        tagService.replaceTags(item.getId(), request.getTagIds());
        return toDetailVO(item);
    }

    @Override
    @Transactional
    public KnowledgeDetailVO update(Long id, KnowledgeSaveRequest request) {
        KnowledgeItem item = requireExisting(id);
        assertWritable(item);

        item.setTitle(request.getTitle().trim());
        item.setSummary(request.getSummary());
        item.setContent(request.getContent());
        if (request.getContentType() != null) {
            item.setContentType(request.getContentType());
        }
        item.setCategoryId(request.getCategoryId());
        itemMapper.updateById(item);

        tagService.replaceTags(id, request.getTagIds());
        return toDetailVO(item);
    }

    @Override
    public KnowledgeDetailVO detail(Long id) {
        KnowledgeItem item = requireExisting(id);
        itemMapper.incrementViewCount(id);
        item.setViewCount(item.getViewCount() == null ? 1 : item.getViewCount() + 1);
        return toDetailVO(item);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        KnowledgeItem item = requireExisting(id);
        assertWritable(item);
        tagService.replaceTags(id, java.util.List.of());
        itemMapper.deleteById(id);
    }

    @Override
    public KnowledgeItem getEntity(Long id) {
        return requireExisting(id);
    }

    private KnowledgeItem requireExisting(Long id) {
        KnowledgeItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("知识条目不存在");
        }
        return item;
    }

    /** 仅作者本人或管理员可写。 */
    private void assertWritable(KnowledgeItem item) {
        User current = CurrentUser.getRequired();
        if (!current.isAdmin() && !item.getAuthorId().equals(current.getId())) {
            throw new BusinessException("无权操作他人的知识条目");
        }
    }

    private KnowledgeDetailVO toDetailVO(KnowledgeItem item) {
        return KnowledgeDetailVO.from(item);
    }
}
```

- [ ] **Step 9: 创建 `TagServiceImpl.java` 的最小实现**

```java
package com.njupt.kms.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.njupt.kms.knowledge.entity.ItemTag;
import com.njupt.kms.knowledge.mapper.ItemTagMapper;
import com.njupt.kms.knowledge.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final ItemTagMapper itemTagMapper;

    @Override
    @Transactional
    public void replaceTags(Long itemId, List<Long> tagIds) {
        itemTagMapper.delete(Wrappers.<ItemTag>lambdaQuery().eq(ItemTag::getItemId, itemId));
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds.stream().distinct().toList()) {
            ItemTag link = new ItemTag();
            link.setItemId(itemId);
            link.setTagId(tagId);
            itemTagMapper.insert(link);
        }
    }
}
```

- [ ] **Step 10: 创建 `ItemTag.java` 与 `ItemTagMapper.java`**

```java
package com.njupt.kms.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("item_tag")
public class ItemTag {

    private Long itemId;

    private Long tagId;
}
```

```java
package com.njupt.kms.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njupt.kms.knowledge.entity.ItemTag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ItemTagMapper extends BaseMapper<ItemTag> {
}
```

- [ ] **Step 11: 创建 `KnowledgeController.java`**

```java
package com.njupt.kms.knowledge.controller;

import com.njupt.kms.common.Result;
import com.njupt.kms.knowledge.dto.KnowledgeDetailVO;
import com.njupt.kms.knowledge.dto.KnowledgeSaveRequest;
import com.njupt.kms.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping("/{id}")
    public Result<KnowledgeDetailVO> detail(@PathVariable Long id) {
        return Result.ok(knowledgeService.detail(id));
    }

    @PostMapping
    public Result<KnowledgeDetailVO> create(@Valid @RequestBody KnowledgeSaveRequest request) {
        return Result.ok(knowledgeService.create(request));
    }

    @PutMapping("/{id}")
    public Result<KnowledgeDetailVO> update(@PathVariable Long id,
                                            @Valid @RequestBody KnowledgeSaveRequest request) {
        return Result.ok(knowledgeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok();
    }
}
```

- [ ] **Step 12: 运行测试确认通过**

Run: `cd kms-backend && mvn -q test -Dtest=KnowledgeServiceTest`
Expected: `Tests run: 8, Failures: 0, Errors: 0`

- [ ] **Step 13: 提交**

```bash
git add kms-backend/src
git commit -m "feat(knowledge): add item CRUD with author-based write guard"
```

---

### Task 8: M1 状态流转与列表可见性

**Files:**
- Modify: `kms-backend/src/main/java/com/njupt/kms/knowledge/service/KnowledgeService.java`
- Modify: `kms-backend/src/main/java/com/njupt/kms/knowledge/service/impl/KnowledgeServiceImpl.java`
- Modify: `kms-backend/src/main/java/com/njupt/kms/knowledge/controller/KnowledgeController.java`
- Modify: `kms-backend/src/main/java/com/njupt/kms/knowledge/mapper/KnowledgeItemMapper.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/dto/KnowledgeQuery.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/dto/KnowledgeListVO.java`
- Create: `kms-backend/src/main/resources/mapper/KnowledgeItemMapper.xml`
- Test: `kms-backend/src/test/java/com/njupt/kms/knowledge/KnowledgeStatusTest.java`

**Interfaces:**
- Consumes: Task 7 的 `KnowledgeService`、`KnowledgeItem`；Task 6 的 `CategoryService.descendantIds(Long)`
- Produces:
  - `KnowledgeService.publish(Long id)` → `KnowledgeDetailVO`
  - `KnowledgeService.archive(Long id)` → `KnowledgeDetailVO`
  - `KnowledgeService.restore(Long id)` → `KnowledgeDetailVO`
  - `KnowledgeService.page(KnowledgeQuery)` → `Page<KnowledgeListVO>`
  - 状态流转规则：`DRAFT → PUBLISHED`（publish）、`PUBLISHED → ARCHIVED`（archive）、`ARCHIVED → DRAFT`（restore）、`DRAFT → ARCHIVED`（archive）
  - `KnowledgeQuery` 字段：`categoryId` `tagId` `status` `mine`（默认 false）`page`（默认 1）`size`（默认 10）
  - `KnowledgeListVO` 字段：`id` `title` `summary` `status` `categoryId` `categoryName` `authorName` `viewCount` `updatedAt`

- [ ] **Step 1: 写失败测试 `KnowledgeStatusTest.java`**

```java
package com.njupt.kms.knowledge;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.knowledge.dto.KnowledgeDetailVO;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import com.njupt.kms.knowledge.mapper.KnowledgeItemMapper;
import com.njupt.kms.knowledge.service.KnowledgeService;
import com.njupt.kms.knowledge.service.TagService;
import com.njupt.kms.knowledge.service.impl.KnowledgeServiceImpl;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeStatusTest {

    private KnowledgeItemMapper itemMapper;
    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        itemMapper = mock(KnowledgeItemMapper.class);
        knowledgeService = new KnowledgeServiceImpl(itemMapper, mock(TagService.class));
        User user = new User();
        user.setId(2L);
        user.setRole("USER");
        user.setStatus(1);
        CurrentUser.set(user);
    }

    private KnowledgeItem withStatus(Long id, String status) {
        KnowledgeItem it = new KnowledgeItem();
        it.setId(id);
        it.setTitle("标题");
        it.setStatus(status);
        it.setAuthorId(2L);
        it.setViewCount(0);
        return it;
    }

    @Test
    void publish_moves_draft_to_published_and_stamps_time() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "DRAFT"));

        KnowledgeDetailVO vo = knowledgeService.publish(1L);

        assertThat(vo.getStatus()).isEqualTo("PUBLISHED");
        verifyStamp();
    }

    @Test
    void publish_rejects_already_published() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "PUBLISHED"));

        assertThatThrownBy(() -> knowledgeService.publish(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前状态不允许发布");
    }

    @Test
    void publish_rejects_archived_directly() {
        // 归档态必须先 restore 回草稿再发布，避免绕过编辑
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "ARCHIVED"));

        assertThatThrownBy(() -> knowledgeService.publish(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前状态不允许发布");
    }

    @Test
    void archive_from_published_succeeds() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "PUBLISHED"));

        assertThat(knowledgeService.archive(1L).getStatus()).isEqualTo("ARCHIVED");
        verifyStamp();
    }

    @Test
    void archive_from_draft_succeeds() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "DRAFT"));

        assertThat(knowledgeService.archive(1L).getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    void archive_rejects_already_archived() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "ARCHIVED"));

        assertThatThrownBy(() -> knowledgeService.archive(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前状态不允许归档");
    }

    @Test
    void restore_moves_archived_to_draft() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "ARCHIVED"));

        assertThat(knowledgeService.restore(1L).getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void restore_rejects_published() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "PUBLISHED"));

        assertThatThrownBy(() -> knowledgeService.restore(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前状态不允许恢复");
    }

    @Test
    void transition_rejects_non_author() {
        KnowledgeItem others = withStatus(1L, "DRAFT");
        others.setAuthorId(99L);
        when(itemMapper.selectById(1L)).thenReturn(others);

        assertThatThrownBy(() -> knowledgeService.publish(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权操作他人的知识条目");
    }

    private void verifyStamp() {
        org.mockito.Mockito.verify(itemMapper).updateById(any(KnowledgeItem.class));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd kms-backend && mvn -q test -Dtest=KnowledgeStatusTest`
Expected: 编译失败 —— `publish` / `archive` / `restore` 方法不存在

- [ ] **Step 3: 在 `KnowledgeService.java` 中新增三个方法声明**

在接口中追加：

```java
    KnowledgeDetailVO publish(Long id);

    KnowledgeDetailVO archive(Long id);

    KnowledgeDetailVO restore(Long id);
```

- [ ] **Step 4: 在 `KnowledgeServiceImpl.java` 中实现三个方法**

追加到类中（并补充 `import java.time.LocalDateTime;`）：

```java
    @Override
    @Transactional
    public KnowledgeDetailVO publish(Long id) {
        KnowledgeItem item = requireExisting(id);
        assertWritable(item);
        if (!STATUS_DRAFT.equals(item.getStatus())) {
            throw new BusinessException("当前状态不允许发布");
        }
        item.setStatus(STATUS_PUBLISHED);
        item.setPublishedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        return toDetailVO(item);
    }

    @Override
    @Transactional
    public KnowledgeDetailVO archive(Long id) {
        KnowledgeItem item = requireExisting(id);
        assertWritable(item);
        if (STATUS_ARCHIVED.equals(item.getStatus())) {
            throw new BusinessException("当前状态不允许归档");
        }
        item.setStatus(STATUS_ARCHIVED);
        item.setArchivedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        return toDetailVO(item);
    }

    @Override
    @Transactional
    public KnowledgeDetailVO restore(Long id) {
        KnowledgeItem item = requireExisting(id);
        assertWritable(item);
        if (!STATUS_ARCHIVED.equals(item.getStatus())) {
            throw new BusinessException("当前状态不允许恢复");
        }
        item.setStatus(STATUS_DRAFT);
        item.setArchivedAt(null);
        itemMapper.updateById(item);
        return toDetailVO(item);
    }
```

- [ ] **Step 5: 创建 `KnowledgeQuery.java` 与 `KnowledgeListVO.java`**

```java
package com.njupt.kms.knowledge.dto;

import lombok.Data;

@Data
public class KnowledgeQuery {

    private Long categoryId;

    private Long tagId;

    /** DRAFT / PUBLISHED / ARCHIVED；为空时按 mine 决定可见范围 */
    private String status;

    /** true 表示只看当前用户的条目（含全部状态） */
    private Boolean mine = false;

    private Integer page = 1;

    private Integer size = 10;
}
```

```java
package com.njupt.kms.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeListVO {

    private Long id;
    private String title;
    private String summary;
    private String status;
    private Long categoryId;
    private String categoryName;
    private String authorName;
    private Integer viewCount;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 6: 在 `KnowledgeItemMapper.java` 中新增分页查询**

```java
    /**
     * 列表查询。visibleAuthorId 为 null 时只返回 PUBLISHED；
     * 非 null 时返回「PUBLISHED 或该作者自己的条目」（即含自己的草稿与归档）。
     * categoryIds 为空时不限制分类。
     */
    IPage<KnowledgeListVO> selectPageVO(IPage<KnowledgeListVO> page,
                                        @Param("categoryIds") Collection<Long> categoryIds,
                                        @Param("status") String status,
                                        @Param("tagId") Long tagId,
                                        @Param("visibleAuthorId") Long visibleAuthorId);
```

补充 import：

```java
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njupt.kms.knowledge.dto.KnowledgeListVO;
import java.util.Collection;
```

- [ ] **Step 7: 创建 `src/main/resources/mapper/KnowledgeItemMapper.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.njupt.kms.knowledge.mapper.KnowledgeItemMapper">

    <select id="selectPageVO" resultType="com.njupt.kms.knowledge.dto.KnowledgeListVO">
        SELECT k.id,
        k.title,
        k.summary,
        k.status,
        k.category_id  AS categoryId,
        c.name         AS categoryName,
        u.nickname     AS authorName,
        k.view_count   AS viewCount,
        k.updated_at   AS updatedAt
        FROM knowledge_item k
        LEFT JOIN category c ON c.id = k.category_id
        LEFT JOIN `user` u ON u.id = k.author_id
        <where>
            <if test="categoryIds != null and categoryIds.size() > 0">
                AND k.category_id IN
                <foreach collection="categoryIds" item="cid" open="(" separator="," close=")">#{cid}</foreach>
            </if>
            <if test="status != null and status != ''">
                AND k.status = #{status}
            </if>
            <if test="tagId != null">
                AND EXISTS (SELECT 1 FROM item_tag it WHERE it.item_id = k.id AND it.tag_id = #{tagId})
            </if>
            <choose>
                <when test="visibleAuthorId != null">
                    AND (k.status = 'PUBLISHED' OR k.author_id = #{visibleAuthorId})
                </when>
                <otherwise>
                    AND k.status = 'PUBLISHED'
                </otherwise>
            </choose>
        </where>
        ORDER BY k.updated_at DESC, k.id DESC
    </select>
</mapper>
```

- [ ] **Step 8: 在 `KnowledgeServiceImpl.java` 中实现 `page()`**

在类中新增（并补充 `import com.baomidou.mybatisplus.extension.plugins.pagination.Page;`、
`import com.njupt.kms.knowledge.dto.KnowledgeListVO;`、`import com.njupt.kms.knowledge.dto.KnowledgeQuery;`、
`import com.njupt.kms.knowledge.service.CategoryService;`、`import java.util.Collection;`）：

```java
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public Page<KnowledgeListVO> page(KnowledgeQuery query) {
        User current = CurrentUser.get();

        // mine=true 时由 SQL 的 visibleAuthorId 分支放行自己的全部状态；
        // 否则只返回 PUBLISHED。
        Long visibleAuthorId = Boolean.TRUE.equals(query.getMine()) && current != null
                ? current.getId()
                : null;

        // 点击父分类要能看到下级条目，因此取其全部后代 id
        Collection<Long> categoryIds = query.getCategoryId() == null
                ? null
                : categoryService.descendantIds(query.getCategoryId());

        Page<KnowledgeListVO> page = new Page<>(clampPage(query), clampSize(query));
        return (Page<KnowledgeListVO>) itemMapper.selectPageVO(
                page, categoryIds, query.getStatus(), query.getTagId(), visibleAuthorId);
    }

    private int clampPage(KnowledgeQuery query) {
        Integer page = query.getPage();
        return (page == null || page < 1) ? 1 : page;
    }

    private int clampSize(KnowledgeQuery query) {
        Integer size = query.getSize();
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
```

同时在 `KnowledgeServiceImpl` 的构造参数中新增 `CategoryService categoryService`（Lombok `@RequiredArgsConstructor` 会自动生成构造器；**注意这会改变 `KnowledgeServiceTest` 与 `KnowledgeStatusTest` 的构造调用**）：

```java
    private final KnowledgeItemMapper itemMapper;
    private final TagService tagService;
    private final CategoryService categoryService;
```

并将两个测试类中的构造改为：

```java
knowledgeService = new KnowledgeServiceImpl(itemMapper, mock(TagService.class), mock(CategoryService.class));
```

同时把 `KnowledgeService.java` 接口中补上 `page` 声明：

```java
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<KnowledgeListVO> page(KnowledgeQuery query);
```

- [ ] **Step 9: 在 `KnowledgeController.java` 中新增列表端点与三个状态流转端点**

```java
    @GetMapping
    public Result<Page<KnowledgeListVO>> page(KnowledgeQuery query) {
        return Result.ok(knowledgeService.page(query));
    }

    @PostMapping("/{id}/publish")
    public Result<KnowledgeDetailVO> publish(@PathVariable Long id) {
        return Result.ok(knowledgeService.publish(id));
    }

    @PostMapping("/{id}/archive")
    public Result<KnowledgeDetailVO> archive(@PathVariable Long id) {
        return Result.ok(knowledgeService.archive(id));
    }

    @PostMapping("/{id}/restore")
    public Result<KnowledgeDetailVO> restore(@PathVariable Long id) {
        return Result.ok(knowledgeService.restore(id));
    }
```

补充 import：

```java
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njupt.kms.knowledge.dto.KnowledgeListVO;
import com.njupt.kms.knowledge.dto.KnowledgeQuery;
```

- [ ] **Step 10: 运行全部测试确认通过**

Run: `cd kms-backend && mvn -q test`
Expected: 全部通过，`Failures: 0, Errors: 0`

- [ ] **Step 11: 手工验证列表可见性**

```bash
# 未登录：只看得到已发布（20 条中有 16 条 PUBLISHED）
curl -s "http://localhost:8080/api/knowledge?size=100" | grep -o '"status":"[A-Z]*"' | sort | uniq -c
```
Expected: 只出现 `PUBLISHED`

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
# admin 登录后带 mine=true：能看到自己的草稿
curl -s -H "Authorization: $TOKEN" "http://localhost:8080/api/knowledge?mine=true&size=100" \
  | grep -o '"status":"[A-Z]*"' | sort | uniq -c
```
Expected: 同时出现 `PUBLISHED` `DRAFT` `ARCHIVED`

- [ ] **Step 12: 提交**

```bash
git add kms-backend/src
git commit -m "feat(knowledge): add status transitions and visibility-scoped listing"
```

---

### Task 9: M1 标签

**Files:**
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/entity/Tag.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/mapper/TagMapper.java`
- Modify: `kms-backend/src/main/java/com/njupt/kms/knowledge/service/TagService.java`
- Modify: `kms-backend/src/main/java/com/njupt/kms/knowledge/service/impl/TagServiceImpl.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/controller/TagController.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/dto/TagVO.java`
- Test: `kms-backend/src/test/java/com/njupt/kms/knowledge/TagServiceTest.java`

**Interfaces:**
- Consumes: `ItemTag`、`ItemTagMapper`（Task 7）
- Produces:
  - `TagService.listWithCount()` → `List<TagVO>`（按使用次数降序）
  - `TagService.findOrCreate(String name)` → `Tag`
  - `TagService.replaceTagsByNames(Long itemId, List<String> names)` — 按名称覆盖，编辑器直接提交名称
  - `TagService.namesOf(Long itemId)` → `List<String>`
  - `TagVO` 字段：`id` `name` `itemCount`

- [ ] **Step 1: 写失败测试 `TagServiceTest.java`**

```java
package com.njupt.kms.knowledge;

import com.njupt.kms.knowledge.entity.Tag;
import com.njupt.kms.knowledge.mapper.ItemTagMapper;
import com.njupt.kms.knowledge.mapper.TagMapper;
import com.njupt.kms.knowledge.service.TagService;
import com.njupt.kms.knowledge.service.impl.TagServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagServiceTest {

    private TagMapper tagMapper;
    private ItemTagMapper itemTagMapper;
    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagMapper = mock(TagMapper.class);
        itemTagMapper = mock(ItemTagMapper.class);
        tagService = new TagServiceImpl(tagMapper, itemTagMapper);
    }

    @Test
    void find_or_create_reuses_existing_tag() {
        Tag existing = new Tag();
        existing.setId(3L);
        existing.setName("面试");
        when(tagMapper.selectOne(any())).thenReturn(existing);

        Tag result = tagService.findOrCreate("面试");

        assertThat(result.getId()).isEqualTo(3L);
        verify(tagMapper, never()).insert(any(Tag.class));
    }

    @Test
    void find_or_create_inserts_new_tag() {
        when(tagMapper.selectOne(any())).thenReturn(null);
        when(tagMapper.insert(any(Tag.class))).thenAnswer(inv -> {
            ((Tag) inv.getArgument(0)).setId(9L);
            return 1;
        });

        Tag result = tagService.findOrCreate("新标签");

        assertThat(result.getId()).isEqualTo(9L);
    }

    @Test
    void find_or_create_trims_and_ignores_blank() {
        assertThat(tagService.findOrCreate("   ")).isNull();
        assertThat(tagService.findOrCreate(null)).isNull();
    }

    @Test
    void replace_tags_by_names_clears_then_inserts_deduped() {
        Tag java = new Tag();
        java.setId(1L);
        java.setName("Java");
        when(tagMapper.selectOne(any())).thenReturn(java);

        tagService.replaceTagsByNames(5L, List.of("Java", "Java", "  ", "面试"));

        verify(itemTagMapper).delete(any());
        // "Java" 去重后只插一次，空白项被忽略
        verify(itemTagMapper, times(1)).insert(any());
    }

    @Test
    void replace_tags_by_names_accepts_empty_list() {
        tagService.replaceTagsByNames(5L, List.of());

        verify(itemTagMapper).delete(any());
        verify(itemTagMapper, never()).insert(any());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd kms-backend && mvn -q test -Dtest=TagServiceTest`
Expected: 编译失败 —— `Tag`、`TagMapper`、`findOrCreate`、`replaceTagsByNames` 尚不存在

- [ ] **Step 3: 创建 `Tag.java` 与 `TagMapper.java`**

```java
package com.njupt.kms.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tag")
public class Tag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private LocalDateTime createdAt;
}
```

```java
package com.njupt.kms.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njupt.kms.knowledge.dto.TagVO;
import com.njupt.kms.knowledge.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /** 标签及其被引用次数，按引用次数降序。 */
    @Select("""
            SELECT t.id, t.name, COUNT(it.item_id) AS itemCount
            FROM tag t
            LEFT JOIN item_tag it ON it.tag_id = t.id
            GROUP BY t.id, t.name
            ORDER BY itemCount DESC, t.id ASC
            """)
    List<TagVO> selectWithCount();
}
```

- [ ] **Step 4: 创建 `TagVO.java`**

```java
package com.njupt.kms.knowledge.dto;

import lombok.Data;

@Data
public class TagVO {

    private Long id;
    private String name;
    private Integer itemCount;
}
```

- [ ] **Step 5: 扩充 `TagService.java` 接口**

```java
package com.njupt.kms.knowledge.service;

import com.njupt.kms.knowledge.dto.TagVO;
import com.njupt.kms.knowledge.entity.Tag;

import java.util.List;

public interface TagService {

    /** 覆盖式替换某条目的标签（按 id）。传空列表表示清空。 */
    void replaceTags(Long itemId, List<Long> tagIds);

    /** 覆盖式替换某条目的标签（按名称，自动创建不存在的标签）。 */
    void replaceTagsByNames(Long itemId, List<String> names);

    /** 按名称查找，不存在则创建。名称为空白时返回 null。 */
    Tag findOrCreate(String name);

    java.util.Optional<Tag> findByName(String name);

    /** 标签列表，含引用次数。 */
    List<TagVO> listWithCount();
}
```

- [ ] **Step 6: 扩充 `TagServiceImpl.java`**

```java
package com.njupt.kms.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.njupt.kms.knowledge.dto.TagVO;
import com.njupt.kms.knowledge.entity.ItemTag;
import com.njupt.kms.knowledge.entity.Tag;
import com.njupt.kms.knowledge.mapper.ItemTagMapper;
import com.njupt.kms.knowledge.mapper.TagMapper;
import com.njupt.kms.knowledge.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    /** tag.name 上限 50，超出直接截断，避免插入报错 */
    private static final int MAX_NAME_LENGTH = 50;

    private final TagMapper tagMapper;
    private final ItemTagMapper itemTagMapper;

    @Override
    @Transactional
    public void replaceTags(Long itemId, List<Long> tagIds) {
        itemTagMapper.delete(Wrappers.<ItemTag>lambdaQuery().eq(ItemTag::getItemId, itemId));
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            ItemTag link = new ItemTag();
            link.setItemId(itemId);
            link.setTagId(tagId);
            itemTagMapper.insert(link);
        }
    }

    @Override
    @Transactional
    public void replaceTagsByNames(Long itemId, List<String> names) {
        itemTagMapper.delete(Wrappers.<ItemTag>lambdaQuery().eq(ItemTag::getItemId, itemId));
        if (names == null || names.isEmpty()) {
            return;
        }
        // 去重按规范化后的名称，保证 "Java" 与 " java " 不产生两条关联
        List<Tag> tags = names.stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .distinct()
                .map(this::findOrCreate)
                .filter(java.util.Objects::nonNull)
                .toList();

        for (Tag tag : tags) {
            ItemTag link = new ItemTag();
            link.setItemId(itemId);
            link.setTagId(tag.getId());
            itemTagMapper.insert(link);
        }
    }

    @Override
    @Transactional
    public Tag findOrCreate(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String normalized = normalize(name);

        Optional<Tag> existing = findByName(normalized);
        if (existing.isPresent()) {
            return existing.get();
        }

        Tag tag = new Tag();
        tag.setName(normalized);
        try {
            tagMapper.insert(tag);
            return tag;
        } catch (DuplicateKeyException e) {
            // 并发下两个请求同时创建同一标签：重查一次拿到已存在的那条
            return findByName(normalized).orElse(null);
        }
    }

    @Override
    public Optional<Tag> findByName(String name) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                tagMapper.selectOne(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, normalize(name))));
    }

    @Override
    public List<TagVO> listWithCount() {
        return tagMapper.selectWithCount();
    }

    private String normalize(String name) {
        String trimmed = name.trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }
}
```

- [ ] **Step 7: 创建 `TagController.java`**

```java
package com.njupt.kms.knowledge.controller;

import com.njupt.kms.common.Result;
import com.njupt.kms.knowledge.dto.TagVO;
import com.njupt.kms.knowledge.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /** 公开接口：浏览与筛选不需要登录。 */
    @GetMapping
    public Result<List<TagVO>> list() {
        return Result.ok(tagService.listWithCount());
    }
}
```

- [ ] **Step 8: 运行测试确认通过**

Run: `cd kms-backend && mvn -q test -Dtest=TagServiceTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 9: 提交**

```bash
git add kms-backend/src
git commit -m "feat(knowledge): add tag service with find-or-create and usage counts"
```

---

### Task 10: M2 附件上传

**Files:**
- Modify: `kms-backend/pom.xml`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/entity/Attachment.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/mapper/AttachmentMapper.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/storage/FileStorage.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/storage/LocalFileStorage.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/event/AttachmentUploadedEvent.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/service/IngestionService.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/service/impl/IngestionServiceImpl.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/controller/AttachmentController.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/dto/AttachmentVO.java`
- Test: `kms-backend/src/test/java/com/njupt/kms/ingestion/FileStorageTest.java`

**Interfaces:**
- Consumes: `BusinessException`、`CurrentUser`、`KnowledgeService`（Task 7）
- Produces:
  - `FileStorage.store(MultipartFile file)` → `String storedPath`；`load(String storedPath)` → `Resource`；`delete(String storedPath)`
  - `IngestionService.upload(Long itemId, MultipartFile file)` → `AttachmentVO`
  - `IngestionService.listByItem(Long itemId)` → `List<AttachmentVO>`
  - `IngestionService.get(Long id)` → `Attachment`
  - `IngestionService.delete(Long id)`
  - `AttachmentVO` 字段：`id` `itemId` `originalName` `mimeType` `sizeBytes` `parseStatus` `parseError` `textPreview`（前 200 字）`createdAt`
  - 事件 `AttachmentUploadedEvent(Long attachmentId)`

- [ ] **Step 1: 在 `pom.xml` 的 `<dependencies>` 末尾追加解析依赖**

```xml
        <!-- PDF 文本抽取 -->
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>3.0.3</version>
        </dependency>

        <!-- Word（.docx）文本抽取。仅需 poi-ooxml，不引 poi-scratchpad -->
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.3.0</version>
        </dependency>
```

- [ ] **Step 2: 写失败测试 `FileStorageTest.java`**

```java
package com.njupt.kms.ingestion;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.ingestion.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileStorage storage() {
        return new LocalFileStorage(tempDir.toString());
    }

    @Test
    void stores_file_under_dated_directory_and_keeps_extension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "需求规格说明书.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

        String stored = storage().store(file);

        assertThat(stored).endsWith(".pdf").contains("/");
        assertThat(Files.exists(tempDir.resolve(stored))).isTrue();
    }

    @Test
    void rejects_empty_file() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> storage().store(empty))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上传文件为空");
    }

    @Test
    void rejects_executable_extension() {
        MockMultipartFile exe = new MockMultipartFile(
                "file", "virus.exe", "application/octet-stream", "MZ".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storage().store(exe))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    void sanitizes_path_traversal_in_filename() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../etc/passwd.pdf", "application/pdf", "x".getBytes(StandardCharsets.UTF_8));

        String stored = storage().store(file);

        // 存储路径必须落在上传根目录内，不允许跳出
        Path resolved = tempDir.resolve(stored).normalize();
        assertThat(resolved.startsWith(tempDir.normalize())).isTrue();
        assertThat(stored).doesNotContain("..");
    }

    @Test
    void rejects_oversized_file() {
        byte[] big = new byte[21 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", big);

        assertThatThrownBy(() -> storage().store(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过 20MB");
    }

    @Test
    void delete_removes_stored_file() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.md", "text/markdown", "content".getBytes(StandardCharsets.UTF_8));
        LocalFileStorage storage = storage();
        String stored = storage.store(file);

        storage.delete(stored);

        assertThat(Files.exists(tempDir.resolve(stored))).isFalse();
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `cd kms-backend && mvn -q test -Dtest=FileStorageTest`
Expected: 编译失败 —— `LocalFileStorage` 尚不存在

- [ ] **Step 4: 创建 `Attachment.java`**

```java
package com.njupt.kms.ingestion.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attachment")
public class Attachment {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PARSING = "PARSING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itemId;

    private String originalName;

    private String storedPath;

    private String mimeType;

    private Long sizeBytes;

    private String parsedText;

    private String parseStatus;

    private String parseError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: 创建 `AttachmentMapper.java`**

```java
package com.njupt.kms.ingestion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njupt.kms.ingestion.entity.Attachment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {
}
```

- [ ] **Step 6: 创建 `FileStorage.java` 与 `LocalFileStorage.java`**

```java
package com.njupt.kms.ingestion.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

    /** 落盘并返回相对存储路径（相对上传根目录）。 */
    String store(MultipartFile file);

    Resource load(String storedPath);

    void delete(String storedPath);
}
```

```java
package com.njupt.kms.ingestion.storage;

import com.njupt.kms.common.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    /** 允许的扩展名白名单。不放行 .exe/.sh/.jsp 等可执行或脚本类型。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "md", "markdown", "txt", "png", "jpg", "jpeg", "gif", "webp", "mp4");

    private static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;

    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path root;

    public LocalFileStorage(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void prepare() throws IOException {
        Files.createDirectories(root);
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException("文件不能超过 20MB");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支持的文件类型：" + (extension.isEmpty() ? "未知" : extension));
        }

        // 文件名完全丢弃客户端输入，只用扩展名 + UUID，从根源消除路径穿越
        String relativeDir = LocalDate.now().format(MONTH_DIR);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String relativePath = relativeDir + "/" + storedName;

        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("非法的存储路径");
        }

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            log.error("文件写入失败 target={}", target, e);
            throw new BusinessException("文件保存失败，请重试");
        }
        return relativePath;
    }

    @Override
    public Resource load(String storedPath) {
        Path target = root.resolve(storedPath).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("非法的文件路径");
        }
        return new PathResource(target);
    }

    @Override
    public void delete(String storedPath) {
        if (!StringUtils.hasText(storedPath)) {
            return;
        }
        Path target = root.resolve(storedPath).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("文件删除失败 path={}", target, e);
        }
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 7: 创建 `AttachmentUploadedEvent.java`**

```java
package com.njupt.kms.ingestion.event;

/** 附件落盘完成。发布后由 AttachmentParseListener 异步消费并抽取文本。 */
public record AttachmentUploadedEvent(Long attachmentId) {
}
```

- [ ] **Step 8: 创建 `AttachmentVO.java`**

```java
package com.njupt.kms.ingestion.dto;

import com.njupt.kms.ingestion.entity.Attachment;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttachmentVO {

    private static final int PREVIEW_LENGTH = 200;

    private Long id;
    private Long itemId;
    private String originalName;
    private String mimeType;
    private Long sizeBytes;
    private String parseStatus;
    private String parseError;
    private String textPreview;
    private LocalDateTime createdAt;

    public static AttachmentVO from(Attachment attachment) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(attachment.getId());
        vo.setItemId(attachment.getItemId());
        vo.setOriginalName(attachment.getOriginalName());
        vo.setMimeType(attachment.getMimeType());
        vo.setSizeBytes(attachment.getSizeBytes());
        vo.setParseStatus(attachment.getParseStatus());
        vo.setParseError(attachment.getParseError());
        vo.setCreatedAt(attachment.getCreatedAt());

        String text = attachment.getParsedText();
        if (text != null && !text.isBlank()) {
            vo.setTextPreview(text.length() <= PREVIEW_LENGTH
                    ? text
                    : text.substring(0, PREVIEW_LENGTH) + "…");
        }
        return vo;
    }
}
```

- [ ] **Step 9: 创建 `IngestionService.java` 与实现**

```java
package com.njupt.kms.ingestion.service;

import com.njupt.kms.ingestion.dto.AttachmentVO;
import com.njupt.kms.ingestion.entity.Attachment;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IngestionService {

    AttachmentVO upload(Long itemId, MultipartFile file);

    List<AttachmentVO> listByItem(Long itemId);

    Attachment get(Long id);

    Resource download(Long id);

    void delete(Long id);
}
```

```java
package com.njupt.kms.ingestion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.njupt.kms.common.BusinessException;
import com.njupt.kms.ingestion.dto.AttachmentVO;
import com.njupt.kms.ingestion.entity.Attachment;
import com.njupt.kms.ingestion.event.AttachmentUploadedEvent;
import com.njupt.kms.ingestion.mapper.AttachmentMapper;
import com.njupt.kms.ingestion.service.IngestionService;
import com.njupt.kms.ingestion.storage.FileStorage;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import com.njupt.kms.knowledge.service.KnowledgeService;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionServiceImpl implements IngestionService {

    private final AttachmentMapper attachmentMapper;
    private final FileStorage fileStorage;
    private final KnowledgeService knowledgeService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public AttachmentVO upload(Long itemId, MultipartFile file) {
        requireWritableItem(itemId);

        String storedPath = fileStorage.store(file);

        Attachment attachment = new Attachment();
        attachment.setItemId(itemId);
        attachment.setOriginalName(sanitizeName(file.getOriginalFilename()));
        attachment.setStoredPath(storedPath);
        attachment.setMimeType(file.getContentType());
        attachment.setSizeBytes(file.getSize());
        attachment.setParseStatus(Attachment.STATUS_PENDING);
        attachmentMapper.insert(attachment);

        // 事务提交后才触发解析，避免解析线程读到未提交的记录
        eventPublisher.publishEvent(new AttachmentUploadedEvent(attachment.getId()));
        return AttachmentVO.from(attachment);
    }

    @Override
    public List<AttachmentVO> listByItem(Long itemId) {
        return attachmentMapper.selectList(
                        Wrappers.<Attachment>lambdaQuery()
                                .eq(Attachment::getItemId, itemId)
                                .orderByDesc(Attachment::getId))
                .stream()
                .map(AttachmentVO::from)
                .toList();
    }

    @Override
    public Attachment get(Long id) {
        Attachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }
        return attachment;
    }

    @Override
    public Resource download(Long id) {
        return fileStorage.load(get(id).getStoredPath());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Attachment attachment = get(id);
        requireWritableItem(attachment.getItemId());
        attachmentMapper.deleteById(id);
        fileStorage.delete(attachment.getStoredPath());
    }

    /** 只有条目的作者或管理员能挂附件。 */
    private void requireWritableItem(Long itemId) {
        KnowledgeItem item = knowledgeService.getEntity(itemId);
        User current = CurrentUser.getRequired();
        if (!current.isAdmin() && !item.getAuthorId().equals(current.getId())) {
            throw new BusinessException("无权操作他人的知识条目");
        }
    }

    /** 原始文件名只用于展示，去掉路径分隔符防止前端误用。 */
    private String sanitizeName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "未命名文件";
        }
        String name = originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
```

- [ ] **Step 10: 创建 `AttachmentController.java`**

```java
package com.njupt.kms.ingestion.controller;

import com.njupt.kms.common.Result;
import com.njupt.kms.ingestion.dto.AttachmentVO;
import com.njupt.kms.ingestion.entity.Attachment;
import com.njupt.kms.ingestion.service.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final IngestionService ingestionService;

    @PostMapping("/upload")
    public Result<AttachmentVO> upload(@RequestParam("itemId") Long itemId,
                                       @RequestParam("file") MultipartFile file) {
        return Result.ok(ingestionService.upload(itemId, file));
    }

    @GetMapping("/item/{itemId}")
    public Result<List<AttachmentVO>> listByItem(@PathVariable Long itemId) {
        return Result.ok(ingestionService.listByItem(itemId));
    }

    /** 解析状态与文本预览。前端轮询此接口展示解析进度。 */
    @GetMapping("/{id}/text")
    public Result<AttachmentVO> text(@PathVariable Long id) {
        return Result.ok(AttachmentVO.from(ingestionService.get(id)));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Attachment attachment = ingestionService.get(id);
        Resource resource = ingestionService.download(id);
        String encodedName = URLEncoder.encode(attachment.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ingestionService.delete(id);
        return Result.ok();
    }
}
```

- [ ] **Step 11: 运行测试确认通过**

Run: `cd kms-backend && mvn -q test -Dtest=FileStorageTest`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 12: 提交**

```bash
git add kms-backend/pom.xml kms-backend/src
git commit -m "feat(ingestion): add attachment upload with extension allowlist and path-traversal guard"
```

---

### Task 11: M2 异步文本解析

**Files:**
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/parser/TextParser.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/parser/ParserFactory.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/parser/PdfParser.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/parser/DocxParser.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/parser/MarkdownParser.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/parser/ImageParser.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/ingestion/listener/AttachmentParseListener.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/config/AsyncConfig.java`
- Test: `kms-backend/src/test/java/com/njupt/kms/ingestion/ParserFactoryTest.java`

**Interfaces:**
- Consumes: `AttachmentUploadedEvent`、`AttachmentMapper`、`FileStorage`（Task 10）
- Produces:
  - `TextParser.supports(String extension)` → `boolean`；`parse(InputStream in)` → `String`
  - `ParserFactory.forFilename(String filename)` → `TextParser`（找不到抛 `BusinessException("不支持的文件类型：xxx")`）

- [ ] **Step 1: 写失败测试 `ParserFactoryTest.java`**

```java
package com.njupt.kms.ingestion;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.ingestion.parser.DocxParser;
import com.njupt.kms.ingestion.parser.ImageParser;
import com.njupt.kms.ingestion.parser.MarkdownParser;
import com.njupt.kms.ingestion.parser.ParserFactory;
import com.njupt.kms.ingestion.parser.PdfParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserFactoryTest {

    private final ParserFactory factory = new ParserFactory(List.of(
            new PdfParser(), new DocxParser(), new MarkdownParser(), new ImageParser()));

    @Test
    void resolves_parser_by_extension_ignoring_case() {
        assertThat(factory.forFilename("报告.PDF")).isInstanceOf(PdfParser.class);
        assertThat(factory.forFilename("notes.md")).isInstanceOf(MarkdownParser.class);
        assertThat(factory.forFilename("a.docx")).isInstanceOf(DocxParser.class);
        assertThat(factory.forFilename("p.png")).isInstanceOf(ImageParser.class);
    }

    @Test
    void resolves_full_path_filename() {
        assertThat(factory.forFilename("uploads/202610/abc123.pdf")).isInstanceOf(PdfParser.class);
    }

    @Test
    void rejects_unknown_extension() {
        assertThatThrownBy(() -> factory.forFilename("archive.zip"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    void rejects_null_or_extensionless_filename() {
        assertThatThrownBy(() -> factory.forFilename(null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> factory.forFilename("noextension"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markdown_parser_reads_plain_text() throws Exception {
        MarkdownParser parser = new MarkdownParser();
        String content = "# 标题\n\n正文内容";

        String parsed = parser.parse(new java.io.ByteArrayInputStream(
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(parsed).contains("标题").contains("正文内容");
    }

    @Test
    void image_parser_returns_placeholder_without_text() throws Exception {
        ImageParser parser = new ImageParser();

        String parsed = parser.parse(new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertThat(parsed).isNotNull();
        assertThat(parser.supports("png")).isTrue();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd kms-backend && mvn -q test -Dtest=ParserFactoryTest`
Expected: 编译失败 —— 解析器类尚不存在

- [ ] **Step 3: 创建 `TextParser.java`**

```java
package com.njupt.kms.ingestion.parser;

import java.io.InputStream;

/** 文件文本抽取器。每种支持的扩展名对应一个实现。 */
public interface TextParser {

    /** 扩展名（小写，不含点），如 "pdf"。 */
    String extension();

    /** 抽取纯文本。失败时抛异常，由调用方记录到 parse_error。 */
    String parse(InputStream input) throws Exception;
}
```

- [ ] **Step 4: 创建 `ParserFactory.java`**

```java
package com.njupt.kms.ingestion.parser;

import com.njupt.kms.common.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ParserFactory {

    private final Map<String, TextParser> parsers = new HashMap<>();

    public ParserFactory(List<TextParser> available) {
        for (TextParser parser : available) {
            parsers.put(parser.extension().toLowerCase(Locale.ROOT), parser);
        }
    }

    /** 按文件名（可含路径）解析出对应解析器。 */
    public TextParser forFilename(String filename) {
        String extension = extensionOf(filename);
        TextParser parser = parsers.get(extension);
        if (parser == null) {
            throw new BusinessException("不支持的文件类型：" + (extension.isEmpty() ? "未知" : extension));
        }
        return parser;
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 5: 创建 `PdfParser.java`**

```java
package com.njupt.kms.ingestion.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
public class PdfParser implements TextParser {

    @Override
    public String extension() {
        return "pdf";
    }

    @Override
    public String parse(InputStream input) throws Exception {
        try (PDDocument document = Loader.loadPDF(input.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }
}
```

- [ ] **Step 6: 创建 `DocxParser.java`**

```java
package com.njupt.kms.ingestion.parser;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class DocxParser implements TextParser {

    /**
     * 只声明 docx。doc（OLE2 格式）需 poi-scratchpad 且解析质量差，
     * v0.0.1 不支持——传入 .doc 会得到明确的"不支持的文件类型"提示。
     */
    @Override
    public String extension() {
        return "docx";
    }

    @Override
    public String parse(InputStream input) throws Exception {
        try (XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
}
```

- [ ] **Step 7: 创建 `MarkdownParser.java` 与 `ImageParser.java`**

```java
package com.njupt.kms.ingestion.parser;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class MarkdownParser implements TextParser {

    /** 同时支持 .md 与 .markdown 两种扩展名（见 ParserFactory 注册）。 */
    @Override
    public String extension() {
        return "md";
    }

    @Override
    public String parse(InputStream input) throws Exception {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
}
```

`MarkdownParser` 还需支持 `.markdown`。在 `ParserFactory` 中额外注册别名，或让 `MarkdownParser` 同时注册两个扩展名。**采用后者**：给 `MarkdownParser` 加一个 `extensions()` 默认方法。

修改 `TextParser` 接口，新增默认方法：

```java
    /** 该解析器支持的全部扩展名。默认只有 extension() 一个。 */
    default java.util.List<String> extensions() {
        return java.util.List.of(extension());
    }
```

修改 `ParserFactory` 构造器注册逻辑：

```java
        for (TextParser parser : available) {
            for (String ext : parser.extensions()) {
                parsers.put(ext.toLowerCase(Locale.ROOT), parser);
            }
        }
```

并在 `MarkdownParser` 中覆写：

```java
    @Override
    public java.util.List<String> extensions() {
        return java.util.List.of("md", "markdown", "txt");
    }
```

```java
package com.njupt.kms.ingestion.parser;

import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 图片解析器：v0.0.1 不做 OCR，直接返回空文本并标记解析完成。
 * 存在此实现的意义是让图片走完整的解析状态机（PENDING → DONE），
 * 前端展示一致，后续接入 OCR 只需改动本类。
 */
@Component
public class ImageParser implements TextParser {

    @Override
    public String extension() {
        return "png";
    }

    @Override
    public java.util.List<String> extensions() {
        return java.util.List.of("png", "jpg", "jpeg", "gif", "webp");
    }

    @Override
    public String parse(InputStream input) {
        return "";
    }
}
```

- [ ] **Step 8: 创建 `AttachmentParseListener.java`**

```java
package com.njupt.kms.ingestion.listener;

import com.njupt.kms.ingestion.entity.Attachment;
import com.njupt.kms.ingestion.event.AttachmentUploadedEvent;
import com.njupt.kms.ingestion.mapper.AttachmentMapper;
import com.njupt.kms.ingestion.parser.ParserFactory;
import com.njupt.kms.ingestion.parser.TextParser;
import com.njupt.kms.ingestion.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentParseListener {

    /** parse_error 列宽 500，超出会插入失败，故先截断 */
    private static final int MAX_ERROR_LENGTH = 500;

    private final AttachmentMapper attachmentMapper;
    private final FileStorage fileStorage;
    private final ParserFactory parserFactory;

    /**
     * 事务提交后才解析，避免异步线程读到尚未提交的附件记录。
     * 整体 try-catch：解析失败绝不能冒泡——它已在独立线程中，
     * 抛出只会丢失错误信息，且不应影响上传本身的成功。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUploaded(AttachmentUploadedEvent event) {
        Long id = event.attachmentId();
        Attachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            log.warn("附件记录不存在，跳过解析 id={}", id);
            return;
        }

        markStatus(attachment, Attachment.STATUS_PARSING, null);

        try {
            TextParser parser = parserFactory.forFilename(attachment.getOriginalName());
            String text;
            try (InputStream in = fileStorage.load(attachment.getStoredPath()).getInputStream()) {
                text = parser.parse(in);
            }

            attachment.setParsedText(text);
            markStatus(attachment, Attachment.STATUS_DONE, null);
            log.info("附件解析完成 id={} 字符数={}", id, text == null ? 0 : text.length());

        } catch (Exception e) {
            log.error("附件解析失败 id={} name={}", id, attachment.getOriginalName(), e);
            markStatus(attachment, Attachment.STATUS_FAILED, truncate(e.getMessage()));
        }
    }

    private void markStatus(Attachment attachment, String status, String error) {
        attachment.setParseStatus(status);
        attachment.setParseError(error);
        attachmentMapper.updateById(attachment);
    }

    private String truncate(String message) {
        if (message == null) {
            return "解析失败";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
```

- [ ] **Step 9: 创建 `AsyncConfig.java`**

```java
package com.njupt.kms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /**
     * 解析专用线程池。有界队列 + CallerRunsPolicy：
     * 队列满时由调用线程执行，形成天然背压，避免任务被静默丢弃。
     */
    @Bean("attachmentParseExecutor")
    public Executor attachmentParseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("attach-parse-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

并在 `AttachmentParseListener` 的 `@Async` 上指定执行器：

```java
    @Async("attachmentParseExecutor")
```

- [ ] **Step 10: 运行测试确认通过**

Run: `cd kms-backend && mvn -q test -Dtest=ParserFactoryTest`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 11: 手工验证异步解析**

```bash
cd kms-backend && mvn -q spring-boot:run
```

准备一个测试 Markdown 文件后：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

echo "# 测试附件

这是用于验证异步解析的正文内容。" > /tmp/test-attach.md

curl -s -X POST http://localhost:8080/api/attachments/upload \
  -H "Authorization: $TOKEN" \
  -F "itemId=1" -F "file=@/tmp/test-attach.md"

# 立即查询：状态应为 PENDING 或 PARSING
sleep 2
curl -s http://localhost:8080/api/attachments/1/text | head -c 400
```

Expected: `parseStatus` 最终为 `DONE`，`textPreview` 含"验证异步解析"

- [ ] **Step 12: 提交**

```bash
git add kms-backend/src
git commit -m "feat(ingestion): add async text extraction with pluggable parsers"
```

---

### Task 12: M3 检索（全文 + 加权 + 高级过滤）

**Files:**
- Create: `kms-backend/src/main/java/com/njupt/kms/search/dto/SearchQuery.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/search/dto/SearchHit.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/search/service/SearchService.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/search/service/impl/SearchServiceImpl.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/search/controller/SearchController.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/config/SearchProperties.java`
- **M1 侧（知识资产域导出只读端口，拥有 `knowledge_item` 的 SQL）**：
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/dto/KnowledgeSearchCondition.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/dto/KnowledgeSearchRow.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/service/KnowledgeSearchPort.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/service/impl/KnowledgeSearchPortImpl.java`
- Create: `kms-backend/src/main/java/com/njupt/kms/knowledge/mapper/KnowledgeSearchMapper.java`
- Create: `kms-backend/src/main/resources/mapper/KnowledgeSearchMapper.xml`
- Test: `kms-backend/src/test/java/com/njupt/kms/search/SearchQueryBuilderTest.java`

**Interfaces:**
- Consumes: `KnowledgeSearchPort`（M1 导出）、`CategoryService`（求后代 id）、`CurrentUser`、`SearchProperties`
- Produces:
  - `SearchService.search(SearchQuery)` → `Page<SearchHit>`
  - `SearchQuery` 字段：`keyword` `categoryId` `tagId` `contentType` `status` `from`（`LocalDate`）`to`（`LocalDate`）`sort`（`relevance`/`latest`/`hottest`）`mine` `page` `size`
  - `SearchHit` 字段：`id` `title` `snippet` `categoryId` `categoryName` `tags`（`List<String>`）`status` `updatedAt` `score`
  - `SearchProperties`：`weight.title` / `weight.summary` / `weight.content`（前缀 `app.search`）
  - `KnowledgeSearchPort.search(KnowledgeSearchCondition, long page, long size)` → `IPage<KnowledgeSearchRow>`
  - `KnowledgeSearchPort.tagNamesOf(Collection<Long> itemIds)` → `Map<Long, List<String>>`

**为什么 M1 和 M3 的 DTO 要各有一套**：`knowledge_item` 表由 M1 拥有，只有 M1 的 Mapper 能写针对它的 SQL（设计文档 §3.3 铁律）。所以 M1 定义 `KnowledgeSearchRow` 承载原始行，M3 把它映射成自己对外暴露的 `SearchHit`。这样依赖方向单向——`search` 依赖 `knowledge`，`knowledge` 完全不认识 `search`。

- [ ] **Step 1: 写失败测试 `SearchQueryBuilderTest.java`**

`SearchServiceImpl` 中把「参数 → SQL 条件」的转换抽成静态方法，便于独立测试。

```java
package com.njupt.kms.search;

import com.njupt.kms.search.dto.SearchQuery;
import com.njupt.kms.search.service.impl.SearchServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryBuilderTest {

    @Test
    void keyword_is_trimmed_and_null_normalized() {
        SearchQuery query = new SearchQuery();
        query.setKeyword("  Java  ");
        assertThat(SearchServiceImpl.normalizeKeyword(query)).isEqualTo("Java");

        query.setKeyword("   ");
        assertThat(SearchServiceImpl.normalizeKeyword(query)).isNull();

        query.setKeyword(null);
        assertThat(SearchServiceImpl.normalizeKeyword(query)).isNull();
    }

    @Test
    void default_sort_is_relevance() {
        assertThat(SearchServiceImpl.resolveSortOrder(new SearchQuery())).isEqualTo("relevance");
    }

    @Test
    void unknown_sort_falls_back_to_relevance() {
        SearchQuery query = new SearchQuery();
        query.setSort("garbage");
        assertThat(SearchServiceImpl.resolveSortOrder(query)).isEqualTo("relevance");
    }

    @Test
    void known_sorts_are_preserved() {
        SearchQuery query = new SearchQuery();
        query.setSort("latest");
        assertThat(SearchServiceImpl.resolveSortOrder(query)).isEqualTo("latest");
        query.setSort("hottest");
        assertThat(SearchServiceImpl.resolveSortOrder(query)).isEqualTo("hottest");
    }

    @Test
    void page_and_size_are_clamped() {
        SearchQuery query = new SearchQuery();
        query.setPage(0);
        query.setSize(1000);
        assertThat(SearchServiceImpl.clampPage(query)).isEqualTo(1);
        // 上限 100，防止一次拉取过多数据
        assertThat(SearchServiceImpl.clampSize(query)).isEqualTo(100);
    }

    @Test
    void keyword_with_han_characters_uses_fulltext_path() {
        assertThat(SearchServiceImpl.prefersFullText("索引")).isTrue();
        assertThat(SearchServiceImpl.prefersFullText("数据库")).isTrue();
        assertThat(SearchServiceImpl.prefersFullText("MySQL 索引")).isTrue();
    }

    @Test
    void ascii_only_keyword_uses_like_path_because_ngram_skips_latin() {
        // 实测：ngram 解析器不索引拉丁字符，MATCH('Java') 恒为 0 分。
        // 纯英文/数字关键词必须降级到 LIKE 路径，否则搜索结果为空。
        assertThat(SearchServiceImpl.prefersFullText("Java")).isFalse();
        assertThat(SearchServiceImpl.prefersFullText("MySQL")).isFalse();
        assertThat(SearchServiceImpl.prefersFullText("JVM")).isFalse();
        assertThat(SearchServiceImpl.prefersFullText("Spring 6")).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd kms-backend && mvn -q test -Dtest=SearchQueryBuilderTest`
Expected: 编译失败 —— `SearchQuery`、`SearchServiceImpl` 尚不存在

- [ ] **Step 3: 创建 `SearchQuery.java`**

```java
package com.njupt.kms.search.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SearchQuery {

    private String keyword;

    private Long categoryId;

    private Long tagId;

    private String contentType;

    /** DRAFT / PUBLISHED / ARCHIVED；为空时按 mine 决定 */
    private String status;

    private LocalDate from;

    private LocalDate to;

    /** relevance（默认） / latest / hottest */
    private String sort;

    /** true 表示只搜索当前用户的条目（含全部状态） */
    private Boolean mine = false;

    private Integer page = 1;

    private Integer size = 10;
}
```

- [ ] **Step 4: 创建 `SearchHit.java`**

```java
package com.njupt.kms.search.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SearchHit {

    private Long id;
    private String title;
    /** 纯文本命中片段，不含 HTML。高亮由前端完成，避免后端拼 HTML 引入 XSS。 */
    private String snippet;
    private Long categoryId;
    private String categoryName;
    private List<String> tags = new ArrayList<>();
    private String status;
    private LocalDateTime updatedAt;
    private Double score;
}
```

- [ ] **Step 5: 创建 `SearchProperties.java`**

```java
package com.njupt.kms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 检索相关度权重。标题是内容的最强语义浓缩，摘要次之，正文兜底。
 * 配置化而非硬编码，便于在报告中说明参数调优过程。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.search")
public class SearchProperties {

    private Weight weight = new Weight();

    @Data
    public static class Weight {
        private double title = 3.0;
        private double summary = 2.0;
        private double content = 1.0;
    }
}
```

- [ ] **Step 6: 在 M1（`knowledge` 包）中创建只读端口的 DTO 与接口**

```java
package com.njupt.kms.knowledge.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.Collection;

/** M3 传给 M1 的检索条件。M1 据此拼 SQL，不感知 M3 的任何类型。 */
@Data
public class KnowledgeSearchCondition {

    private String keyword;

    /** 已展开为「本分类 + 全部后代」的 id 集合；为空表示不限分类 */
    private Collection<Long> categoryIds;

    private Long tagId;

    private String contentType;

    private String status;

    private LocalDate from;

    private LocalDate to;

    /** relevance / latest / hottest */
    private String sort;

    /** 非 null 时额外放行该作者的未发布条目 */
    private Long visibleAuthorId;

    private double weightTitle;
    private double weightSummary;
    private double weightContent;

    /**
     * true 走 ngram 全文索引（关键词含 CJK）；false 走 LIKE（纯 ASCII 关键词）。
     * 依据见设计文档 §5.2 第 3 点：ngram 解析器不索引拉丁字符。
     */
    private boolean useFullText;
}
```

```java
package com.njupt.kms.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 检索命中的知识条目原始行。只有 M1 生产它。 */
@Data
public class KnowledgeSearchRow {

    private Long id;
    private String title;
    private String summary;
    private Long categoryId;
    private String categoryName;
    private String status;
    private LocalDateTime updatedAt;
    /** 加权相关度；无关键词时为 0 */
    private Double score;
}
```

```java
package com.njupt.kms.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njupt.kms.knowledge.dto.KnowledgeSearchCondition;
import com.njupt.kms.knowledge.dto.KnowledgeSearchRow;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * M1 导出的**只读**端口，供 M3 检索域跨模块取数。
 *
 * <p>{@code knowledge_item} 表由 M1 拥有，因此针对它的 SQL 只能写在 M1 包内
 * （设计文档 §3.3）。M3 通过本接口取数，依赖方向单向：search → knowledge。
 *
 * <p>本接口不含任何写操作，这是「受控例外」得以成立的前提。
 */
public interface KnowledgeSearchPort {

    IPage<KnowledgeSearchRow> search(KnowledgeSearchCondition condition, long page, long size);

    /** 批量取条目标签名，避免 M3 逐条查库。返回 itemId → 标签名列表。 */
    Map<Long, List<String>> tagNamesOf(Collection<Long> itemIds);
}
```

- [ ] **Step 7: 在 M1 中创建 `KnowledgeSearchMapper.java` 与 `KnowledgeSearchMapper.xml`**

```java
package com.njupt.kms.knowledge.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njupt.kms.knowledge.dto.KnowledgeSearchRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Collection;

@Mapper
public interface KnowledgeSearchMapper {

    IPage<KnowledgeSearchRow> search(IPage<KnowledgeSearchRow> page,
                                     @Param("kw") String keyword,
                                     @Param("wTitle") double wTitle,
                                     @Param("wSummary") double wSummary,
                                     @Param("wContent") double wContent,
                                     @Param("categoryIds") Collection<Long> categoryIds,
                                     @Param("tagId") Long tagId,
                                     @Param("contentType") String contentType,
                                     @Param("status") String status,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to,
                                     @Param("sort") String sort,
                                     @Param("visibleAuthorId") Long visibleAuthorId,
                                     @Param("useFullText") boolean useFullText);

    java.util.List<java.util.Map<String, Object>> selectTagsOf(
            @Param("itemIds") Collection<Long> itemIds);
}
```

创建 `src/main/resources/mapper/KnowledgeSearchMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.njupt.kms.knowledge.mapper.KnowledgeSearchMapper">

    <!-- 路径 A：含 CJK 的关键词 —— 走三个 ngram 全文索引分别求分再加权。
         MySQL 要求 MATCH() 的列与某个全文索引的列完全一致，故必须分列书写。 -->
    <sql id="fullTextScore">
        (
        #{wTitle}   * MATCH(k.title)   AGAINST (#{kw} IN NATURAL LANGUAGE MODE)
        + #{wSummary} * MATCH(k.summary) AGAINST (#{kw} IN NATURAL LANGUAGE MODE)
        + #{wContent} * MATCH(k.content) AGAINST (#{kw} IN NATURAL LANGUAGE MODE)
        )
    </sql>

    <!-- 路径 B：纯 ASCII 的关键词 —— ngram 解析器不索引拉丁字符，MATCH 恒为 0 分，
         故降级为 LIKE。权重取值与路径 A 相同，保证两种关键词下排序语义一致。
         详见设计文档 §5.2 第 3 点与 §6.3.1。 -->
    <sql id="likeScore">
        (
        CASE WHEN k.title   LIKE CONCAT('%', #{kw}, '%') THEN #{wTitle}   ELSE 0 END
        + CASE WHEN k.summary LIKE CONCAT('%', #{kw}, '%') THEN #{wSummary} ELSE 0 END
        + CASE WHEN k.content LIKE CONCAT('%', #{kw}, '%') THEN #{wContent} ELSE 0 END
        )
    </sql>

    <select id="search" resultType="com.njupt.kms.knowledge.dto.KnowledgeSearchRow">
        SELECT k.id,
        k.title,
        k.summary AS snippet,
        k.category_id AS categoryId,
        c.name AS categoryName,
        k.status,
        k.updated_at AS updatedAt
        <choose>
            <when test="kw != null and kw != '' and useFullText">
                , <include refid="fullTextScore"/> AS score
            </when>
            <when test="kw != null and kw != ''">
                , <include refid="likeScore"/> AS score
            </when>
            <otherwise>
                , 0 AS score
            </otherwise>
        </choose>
        FROM knowledge_item k
        LEFT JOIN category c ON c.id = k.category_id
        <where>
            <if test="kw != null and kw != '' and useFullText">
                AND (
                MATCH(k.title) AGAINST (#{kw} IN NATURAL LANGUAGE MODE)
                OR MATCH(k.summary) AGAINST (#{kw} IN NATURAL LANGUAGE MODE)
                OR MATCH(k.content) AGAINST (#{kw} IN NATURAL LANGUAGE MODE)
                )
            </if>
            <if test="kw != null and kw != '' and !useFullText">
                AND (
                k.title LIKE CONCAT('%', #{kw}, '%')
                OR k.summary LIKE CONCAT('%', #{kw}, '%')
                OR k.content LIKE CONCAT('%', #{kw}, '%')
                )
            </if>
            <if test="categoryIds != null and categoryIds.size() > 0">
                AND k.category_id IN
                <foreach collection="categoryIds" item="cid" open="(" separator="," close=")">#{cid}</foreach>
            </if>
            <if test="tagId != null">
                AND EXISTS (SELECT 1 FROM item_tag it WHERE it.item_id = k.id AND it.tag_id = #{tagId})
            </if>
            <if test="contentType != null and contentType != ''">
                AND k.content_type = #{contentType}
            </if>
            <if test="status != null and status != ''">
                AND k.status = #{status}
            </if>
            <if test="from != null">
                AND k.updated_at &gt;= #{from}
            </if>
            <if test="to != null">
                AND k.updated_at &lt; DATE_ADD(#{to}, INTERVAL 1 DAY)
            </if>
            <choose>
                <when test="visibleAuthorId != null">
                    AND (k.status = 'PUBLISHED' OR k.author_id = #{visibleAuthorId})
                </when>
                <otherwise>
                    AND k.status = 'PUBLISHED'
                </otherwise>
            </choose>
        </where>
        <choose>
            <when test="sort == 'latest'">
                ORDER BY k.updated_at DESC, k.id DESC
            </when>
            <when test="sort == 'hottest'">
                ORDER BY k.view_count DESC, k.id DESC
            </when>
            <otherwise>
                ORDER BY score DESC, k.updated_at DESC, k.id DESC
            </otherwise>
        </choose>
    </select>

    <!-- 命中条目的标签名，供前端展示。避免 N+1，一次查回。 -->
    <select id="selectTagsOf" resultType="java.util.Map">
        SELECT it.item_id AS itemId, t.name AS name
        FROM item_tag it
        JOIN tag t ON t.id = it.tag_id
        WHERE it.item_id IN
        <foreach collection="itemIds" item="iid" open="(" separator="," close=")">#{iid}</foreach>
        ORDER BY t.name
    </select>
</mapper>
```

- [ ] **Step 8: 在 M1 中创建 `KnowledgeSearchPortImpl.java`**

```java
package com.njupt.kms.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njupt.kms.knowledge.dto.KnowledgeSearchCondition;
import com.njupt.kms.knowledge.dto.KnowledgeSearchRow;
import com.njupt.kms.knowledge.mapper.KnowledgeSearchMapper;
import com.njupt.kms.knowledge.service.KnowledgeSearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KnowledgeSearchPortImpl implements KnowledgeSearchPort {

    private final KnowledgeSearchMapper searchMapper;

    @Override
    public IPage<KnowledgeSearchRow> search(KnowledgeSearchCondition condition, long page, long size) {
        Page<KnowledgeSearchRow> pager = new Page<>(page, size);
        return searchMapper.search(
                pager,
                condition.getKeyword(),
                condition.getWeightTitle(),
                condition.getWeightSummary(),
                condition.getWeightContent(),
                condition.getCategoryIds(),
                condition.getTagId(),
                condition.getContentType(),
                condition.getStatus(),
                condition.getFrom(),
                condition.getTo(),
                condition.getSort(),
                condition.getVisibleAuthorId(),
                condition.isUseFullText());
    }

    @Override
    public Map<Long, List<String>> tagNamesOf(Collection<Long> itemIds) {
        Map<Long, List<String>> result = new HashMap<>();
        if (itemIds == null || itemIds.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : searchMapper.selectTagsOf(itemIds)) {
            Long itemId = ((Number) row.get("itemId")).longValue();
            result.computeIfAbsent(itemId, k -> new ArrayList<>())
                    .add(String.valueOf(row.get("name")));
        }
        return result;
    }
}
```

- [ ] **Step 9: 创建 M3 的 `SearchService.java` 接口**

```java
package com.njupt.kms.search.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njupt.kms.search.dto.SearchHit;
import com.njupt.kms.search.dto.SearchQuery;

public interface SearchService {

    Page<SearchHit> search(SearchQuery query);
}
```

- [ ] **Step 10: 创建 `SearchServiceImpl.java`（调用 M1 端口）**

```java
package com.njupt.kms.search.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njupt.kms.config.SearchProperties;
import com.njupt.kms.knowledge.dto.KnowledgeSearchCondition;
import com.njupt.kms.knowledge.dto.KnowledgeSearchRow;
import com.njupt.kms.knowledge.service.CategoryService;
import com.njupt.kms.knowledge.service.KnowledgeSearchPort;
import com.njupt.kms.search.dto.SearchHit;
import com.njupt.kms.search.dto.SearchQuery;
import com.njupt.kms.search.service.SearchService;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORTS = Set.of("relevance", "latest", "hottest");

    private final KnowledgeSearchPort knowledgeSearchPort;
    private final CategoryService categoryService;
    private final SearchProperties searchProperties;

    @Override
    public Page<SearchHit> search(SearchQuery query) {
        int page = clampPage(query);
        int size = clampSize(query);

        User current = CurrentUser.get();
        Long visibleAuthorId = Boolean.TRUE.equals(query.getMine()) && current != null
                ? current.getId()
                : null;

        String keyword = normalizeKeyword(query);

        KnowledgeSearchCondition condition = new KnowledgeSearchCondition();
        condition.setKeyword(keyword);
        // ngram 解析器不索引拉丁字符：含 CJK 走全文索引，纯 ASCII 降级 LIKE
        condition.setUseFullText(prefersFullText(keyword));
        // 点击父分类要能看到下级条目，因此展开为全部后代 id
        condition.setCategoryIds(query.getCategoryId() == null
                ? null
                : categoryService.descendantIds(query.getCategoryId()));
        condition.setTagId(query.getTagId());
        condition.setContentType(query.getContentType());
        condition.setStatus(query.getStatus());
        condition.setFrom(query.getFrom());
        condition.setTo(query.getTo());
        condition.setSort(resolveSortOrder(query));
        condition.setVisibleAuthorId(visibleAuthorId);
        condition.setWeightTitle(searchProperties.getWeight().getTitle());
        condition.setWeightSummary(searchProperties.getWeight().getSummary());
        condition.setWeightContent(searchProperties.getWeight().getContent());

        IPage<KnowledgeSearchRow> rows = knowledgeSearchPort.search(condition, page, size);

        // M1 的原始行 → M3 的对外 DTO。两个模块各自持有自己的类型，避免互相依赖。
        List<SearchHit> hits = rows.getRecords().stream().map(SearchServiceImpl::toHit).toList();
        attachTags(hits);

        Page<SearchHit> result = new Page<>(rows.getCurrent(), rows.getSize(), rows.getTotal());
        result.setRecords(hits);
        return result;
    }

    static SearchHit toHit(KnowledgeSearchRow row) {
        SearchHit hit = new SearchHit();
        hit.setId(row.getId());
        hit.setTitle(row.getTitle());
        hit.setSnippet(row.getSummary());
        hit.setCategoryId(row.getCategoryId());
        hit.setCategoryName(row.getCategoryName());
        hit.setStatus(row.getStatus());
        hit.setUpdatedAt(row.getUpdatedAt());
        hit.setScore(row.getScore());
        return hit;
    }

    /** 批量填充标签，一次查询完成，避免逐条查库。 */
    private void attachTags(List<SearchHit> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(SearchHit::getId).toList();
        Map<Long, List<String>> tagMap = knowledgeSearchPort.tagNamesOf(ids);
        for (SearchHit hit : records) {
            hit.setTags(tagMap.getOrDefault(hit.getId(), new ArrayList<>()));
        }
    }

    static String normalizeKeyword(SearchQuery query) {
        return StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
    }

    /**
     * 关键词是否应走 ngram 全文索引。
     *
     * <p>实测（MySQL 8.0.41，ngram_token_size=2）：ngram 解析器只对 CJK 字符生成 token，
     * 拉丁字母与数字完全不进索引，{@code MATCH(title) AGAINST('Java')} 恒为 0 分。
     * 因此含 CJK 才走全文索引，纯 ASCII 必须降级为 LIKE，否则英文技术名词搜不到。
     * 详见设计文档 §5.2 第 3 点。
     */
    static boolean prefersFullText(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return false;
        }
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            // CJK 统一表意文字 + 扩展 A + 兼容表意文字，覆盖本项目可能出现的全部中文
            if ((c >= 0x4E00 && c <= 0x9FFF)
                    || (c >= 0x3400 && c <= 0x4DBF)
                    || (c >= 0xF900 && c <= 0xFAFF)) {
                return true;
            }
        }
        return false;
    }

    static String resolveSortOrder(SearchQuery query) {
        String sort = query.getSort();
        return (sort != null && ALLOWED_SORTS.contains(sort)) ? sort : "relevance";
    }

    static int clampPage(SearchQuery query) {
        Integer page = query.getPage();
        return (page == null || page < 1) ? 1 : page;
    }

    static int clampSize(SearchQuery query) {
        Integer size = query.getSize();
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
```

- [ ] **Step 11: 创建 `SearchController.java`**

```java
package com.njupt.kms.search.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njupt.kms.common.Result;
import com.njupt.kms.search.dto.SearchHit;
import com.njupt.kms.search.dto.SearchQuery;
import com.njupt.kms.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /** 公开接口：未登录只搜到已发布条目。 */
    @GetMapping
    public Result<Page<SearchHit>> search(SearchQuery query) {
        return Result.ok(searchService.search(query));
    }
}
```

- [ ] **Step 12: 运行测试确认通过**

Run: `cd kms-backend && mvn -q test -Dtest=SearchQueryBuilderTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 13: 手工验证加权与过滤**

```bash
# 路径 B（纯 ASCII → LIKE）：英文技术名词必须能搜到，这是本次修正的核心目标
curl -s "http://localhost:8080/api/search?keyword=Java&size=5" | head -c 600

# 路径 A（含 CJK → ngram 全文索引）
curl -s "http://localhost:8080/api/search?keyword=索引&size=5" | head -c 600

# 高级检索：Java + 分类 4（Spring）+ 只看已发布
curl -s "http://localhost:8080/api/search?keyword=Java&categoryId=4&status=PUBLISHED" | head -c 400

# 空关键词 + 分类筛选（纯过滤，走 score=0 分支）
curl -s "http://localhost:8080/api/search?categoryId=11&size=5" | head -c 400
```

Expected:
- **第 1 条必须非空**，且 `Java 基础语法速查`、`Java 集合框架详解` 的 `score` = 3.0（标题命中）。**若此条为空，说明降级逻辑失效**——ngram 索引查不到拉丁词是已实测确认的行为。
- 第 2 条返回 `MySQL 索引原理` 等中文命中，`score` 为 MySQL 计算的 TF-IDF 值（小数）。
- 第 3 条只返回分类 4（Spring）下的条目。
- 第 4 条返回 MySQL 相关条目，不报错（证明空关键词分支可用）。

**验证加权生效**：两条路径下，标题命中的条目都应排在只有正文命中的条目之前。

- [ ] **Step 14: 提交**

```bash
git add kms-backend/src
git commit -m "feat(search): add weighted fulltext search via knowledge read-only port"
```

---

### Task 13: 集成测试与冒烟验证

**Files:**
- Create: `kms-backend/src/test/java/com/njupt/kms/ApiSmokeTest.java`
- Create: `kms-backend/src/test/resources/application-test.yaml`
- Create: `kms-backend/docs/API.md`

**Interfaces:**
- Consumes: 全部已完成任务
- Produces: 端到端可重复的冒烟测试；接口文档

- [ ] **Step 1: 创建 `src/test/resources/application-test.yaml`**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/kms_test?serverTimezone=Asia/Shanghai&useSSL=false&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true
    username: root
    password: 123456
  data:
    redis:
      host: localhost
      port: 6379

app:
  upload-dir: target/test-uploads
```

> 测试专用库 `kms_test`，与开发库 `kms` 隔离。执行 `schema.sql` 与 `seed.sql` 时把库名改为 `kms_test`（或用 `sed` 替换）。

- [ ] **Step 2: 写冒烟测试 `ApiSmokeTest.java`**

```java
package com.njupt.kms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String token;

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return node.path("data").path("token").asText();
    }

    @Test
    @Order(1)
    void public_endpoints_work_without_login() throws Exception {
        mockMvc.perform(get("/api/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/knowledge").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @Order(2)
    void write_endpoints_reject_anonymous() throws Exception {
        mockMvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"匿名创建\"}"))
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    @Order(3)
    void login_succeeds_and_me_returns_profile() throws Exception {
        token = login("admin", "admin123");
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    @Order(4)
    void login_rejects_wrong_password() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @Order(5)
    void rejection_does_not_leak_internal_detail() throws Exception {
        // 传入一个触发系统异常的请求（超长 title 触发校验，此处验证校验消息不泄露结构）
        mockMvc.perform(post("/api/knowledge")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("标题不能为空"));
    }

    @Test
    @Order(6)
    void fulltext_search_finds_chinese_content() throws Exception {
        mockMvc.perform(get("/api/search").param("keyword", "索引"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // seed 中「MySQL 索引原理」必须被搜到，证明 ngram 索引生效
                .andExpect(jsonPath("$.data.records[0].title").value(
                        org.hamcrest.Matchers.containsString("索引")));
    }

    @Test
    @Order(7)
    void search_finds_latin_terms_via_like_fallback() throws Exception {
        // ngram 解析器不索引拉丁字符，此路径由 LIKE 兜底。
        // 若此测试失败，说明英文技术名词搜不到——对本系统是致命缺陷。
        mockMvc.perform(get("/api/search").param("keyword", "Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].title").value(
                        org.hamcrest.Matchers.containsString("Java")));
    }

    @Test
    @Order(8)
    void unknown_item_returns_business_error_not_stacktrace() throws Exception {
        mockMvc.perform(get("/api/knowledge/999999"))
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.message").value("知识条目不存在"));
    }

    @Test
    @Order(9)
    void forbidden_operation_returns_clear_message() throws Exception {
        String other = login("zhangsan", "user123");
        // seed 中 id=1 的条目作者是 admin(1)，zhangsan(2) 无权修改
        mockMvc.perform(post("/api/knowledge/1/publish").header("Authorization", other))
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.message").value("无权操作他人的知识条目"));
    }
}
```

- [ ] **Step 3: 准备测试库并运行**

```bash
mysql -u root -p -e "DROP DATABASE IF EXISTS kms_test; CREATE DATABASE kms_test DEFAULT CHARACTER SET utf8mb4;"
sed 's/^USE kms;/USE kms_test;/; s/CREATE DATABASE IF NOT EXISTS kms/CREATE DATABASE IF NOT EXISTS kms_test/' kms-backend/sql/schema.sql | mysql -u root -p
sed 's/^USE kms;/USE kms_test;/' kms-backend/sql/seed.sql | mysql -u root -p

cd kms-backend && mvn -q test
```

Expected: 全部测试通过，`Failures: 0, Errors: 0`

- [ ] **Step 4: 编写 `kms-backend/docs/API.md`**

按 §7 接口契约，为每个端点写：路径、方法、参数、请求示例、响应示例。**这份文档是前端计划的输入**，字段名必须与实现完全一致。

- [ ] **Step 5: 提交**

```bash
git add kms-backend/src kms-backend/docs
git commit -m "test(backend): add end-to-end smoke suite and API reference"
```

---

## 完成标准

后端计划完成的判定条件（全部满足才算完成）：

- [ ] `mvn -q test` 全绿
- [ ] `mvn -q clean package` 产出可运行 jar
- [ ] 未登录可浏览分类树、标签、已发布条目列表与检索结果
- [ ] 未登录访问写接口返回 `请先登录`
- [ ] 非作者写他人条目返回 `无权操作他人的知识条目`
- [ ] 中文全文检索能命中，且标题命中排在正文命中之前
- [ ] 附件上传后 `parse_status` 自动流转到 `DONE`
- [ ] `git status` 中无 `application-local.yaml`
- [ ] `kms-backend/docs/API.md` 字段名与实现一致（前端计划的依据）
