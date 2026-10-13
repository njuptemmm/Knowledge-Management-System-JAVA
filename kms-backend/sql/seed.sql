-- 知识管理系统 测试数据
-- 密码明文：admin -> admin123 ； zhangsan -> user123
-- 哈希由 BCryptPasswordEncoder 生成（见计划 Task 2 Step 3）

USE kms;

DELETE FROM item_tag;
DELETE FROM attachment;
DELETE FROM knowledge_item;
DELETE FROM tag;
DELETE FROM category;
DELETE FROM `user`;

INSERT INTO `user` (id, username, password_hash, nickname, role, status) VALUES
  (1, 'admin', '$2a$10$ThpvQL7kiOIDxRsCxc6y/Oz5PWRqsdjXNjBt36FXstVtGbHDIvhm2', '管理员', 'ADMIN', 1),
  (2, 'zhangsan', '$2a$10$jcnN9Ud4ymmu2.Nq9i3cse7rZFDpQi85LWt.dZeU/fDStKHDyk7WC', '张三', 'USER', 1);

-- 分类树：3 级层次
INSERT INTO category (id, name, parent_id, level, sort, description) VALUES
  (1,  '编程语言', 0,  1, 1, '编程语言相关知识'),
  (2,  'Java',     1,  2, 1, 'Java 语言'),
  (3,  'Python',   1,  2, 2, 'Python 语言'),
  (4,  'Spring',   2,  3, 1, 'Spring 生态'),
  (5,  'JVM',      2,  3, 2, 'Java 虚拟机'),
  (10, '数据库',   0,  1, 2, '数据库相关知识'),
  (11, 'MySQL',    10, 2, 1, 'MySQL 数据库'),
  (12, 'Redis',    10, 2, 2, 'Redis 缓存'),
  (20, '软件工程', 0,  1, 3, '软件工程相关知识'),
  (21, '需求分析', 20, 2, 1, '需求分析方法'),
  (22, '软件测试', 20, 2, 2, '软件测试方法');

INSERT INTO tag (id, name) VALUES
  (1, '入门'), (2, '进阶'), (3, '面试'), (4, '实战'), (5, '笔记');

-- 知识条目：20 条，覆盖三种状态与多个分类
INSERT INTO knowledge_item (id, title, summary, content, content_type, status, category_id, author_id, view_count, published_at) VALUES
  (1,  'Java 基础语法速查', '变量、运算符、流程控制的要点整理', '# Java 基础语法

变量声明、运算符优先级、if/for/while 的写法与常见陷阱。', 'MARKDOWN', 'PUBLISHED', 2, 1, 120, NOW()),
  (2,  'Java 集合框架详解', 'List、Set、Map 的实现与选型', '# 集合框架

ArrayList 与 LinkedList 的差异、HashMap 的扩容机制。', 'MARKDOWN', 'PUBLISHED', 2, 1, 98, NOW()),
  (3,  'Spring IoC 容器原理', 'Bean 的生命周期与依赖注入', '# Spring IoC

BeanDefinition 加载、实例化、属性填充、初始化。', 'MARKDOWN', 'PUBLISHED', 4, 1, 210, NOW()),
  (4,  'Spring AOP 与动态代理', 'JDK 动态代理与 CGLIB 的区别', '# Spring AOP

切面、切点、通知的概念与实现方式。', 'MARKDOWN', 'PUBLISHED', 4, 2, 156, NOW()),
  (5,  'JVM 内存结构', '堆、栈、方法区与垃圾回收', '# JVM 内存

新生代与老年代的划分，常见 GC 算法。', 'MARKDOWN', 'PUBLISHED', 5, 1, 305, NOW()),
  (6,  'JVM 调优实战记录', '线上 OOM 排查过程', '# 调优记录

通过 jmap 与 MAT 定位内存泄漏。', 'MARKDOWN', 'DRAFT', 5, 1, 0, NULL),
  (7,  'Python 列表推导式', '优雅的循环写法', '# 列表推导式

[x*2 for x in range(10)] 及其性能考量。', 'MARKDOWN', 'PUBLISHED', 3, 2, 77, NOW()),
  (8,  'Python 装饰器入门', '函数式编程的基础工具', '# 装饰器

闭包、函数作为参数、functools.wraps。', 'MARKDOWN', 'PUBLISHED', 3, 2, 65, NOW()),
  (9,  'MySQL 索引原理', 'B+ 树与最左前缀原则', '# 索引原理

聚簇索引与非聚簇索引，回表查询的代价。', 'MARKDOWN', 'PUBLISHED', 11, 1, 402, NOW()),
  (10, 'MySQL 慢查询优化', 'EXPLAIN 的使用方法', '# 慢查询

通过 EXPLAIN 分析执行计划并建立合适索引。', 'MARKDOWN', 'PUBLISHED', 11, 1, 288, NOW()),
  (11, 'MySQL 事务隔离级别', '四种隔离级别与幻读问题', '# 事务

读未提交、读已提交、可重复读、串行化。', 'MARKDOWN', 'PUBLISHED', 11, 2, 190, NOW()),
  (12, 'Redis 数据结构与应用', 'String、Hash、List、Set、ZSet', '# Redis 数据结构

五种基础类型的使用场景。', 'MARKDOWN', 'PUBLISHED', 12, 1, 233, NOW()),
  (13, 'Redis 缓存穿透与雪崩', '常见缓存问题及解决方案', '# 缓存问题

空值缓存、布隆过滤器、过期时间打散。', 'MARKDOWN', 'PUBLISHED', 12, 1, 175, NOW()),
  (14, 'RAG 检索增强生成入门', '大模型与知识库结合的基本思路', '# RAG

文档切块、向量化、相似度检索、上下文注入。', 'MARKDOWN', 'ARCHIVED', 1, 1, 88, NOW()),
  (15, '需求分析方法论', '用例图与用例描述', '# 需求分析

识别参与者、梳理用例、编写用例规约。', 'MARKDOWN', 'PUBLISHED', 21, 2, 143, NOW()),
  (16, '软件测试用例设计', '等价类划分与边界值分析', '# 测试用例

黑盒测试方法，正常与异常路径覆盖。', 'MARKDOWN', 'PUBLISHED', 22, 2, 167, NOW()),
  (17, '单元测试与 Mock 实践', 'JUnit 5 与 Mockito 的使用', '# 单元测试

测试隔离、依赖替身、断言库选型。', 'MARKDOWN', 'PUBLISHED', 22, 1, 121, NOW()),
  (18, 'ER 图设计要点', '实体识别与关系基数', '# ER 图

一对一、一对多、多对多的画法与落地。', 'MARKDOWN', 'PUBLISHED', 21, 2, 99, NOW()),
  (19, '软件架构风格对比', '分层、微内核、事件驱动', '# 架构风格

各风格的适用场景与代价。', 'MARKDOWN', 'PUBLISHED', 20, 1, 211, NOW()),
  (20, '课程设计答辩准备清单', '功能演示与结构描述的要点', '# 答辩准备

演示脚本、结构讲解、测试数据、问题记录。', 'MARKDOWN', 'DRAFT', 20, 1, 0, NULL);

INSERT INTO item_tag (item_id, tag_id) VALUES
  (1,1),(1,5),(2,2),(2,3),(3,2),(3,4),(4,2),(4,3),(5,2),(5,3),
  (7,1),(8,1),(9,2),(9,4),(10,2),(10,4),(11,3),(12,2),(12,4),(13,2),
  (14,2),(15,1),(16,1),(16,5),(17,4),(18,1),(19,2),(20,5);
