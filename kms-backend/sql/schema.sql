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
