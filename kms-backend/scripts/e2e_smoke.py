#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
知识管理系统 后端端到端联通测试。

覆盖全部 22 个接口的正常路径、边界与异常路径，逐条输出实际结果。

前置条件
--------
1. 后端已启动：  cd kms-backend && mvn spring-boot:run
2. 数据库已重置为 seed 初始状态（否则计数类断言会失败）：
       mysql -u root -p -e "DROP DATABASE IF EXISTS kms;"
       mysql --default-character-set=utf8mb4 -u root -p < sql/schema.sql
       mysql --default-character-set=utf8mb4 -u root -p < sql/seed.sql

用法
----
    python -I scripts/e2e_smoke.py [base_url]

默认 base_url 为 http://localhost:8090。

注意：本脚本的所有 HTTP 请求都由本脚本自己编码，不经过 shell。
Git Bash 的控制台是 GBK，用 curl 传中文会发出 GBK 字节导致"中文搜索恒为 0 条"的假象。
"""

import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8090"

PASSED = []
FAILED = []


# --------------------------------------------------------------------------
# 基础设施
# --------------------------------------------------------------------------

def request(method, path, token=None, body=None, raw_body=None,
            content_type="application/json", params=None):
    """发一次请求，返回 (http_status, parsed_json_or_text)。"""
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params, encoding="utf-8")

    data = None
    if raw_body is not None:
        data = raw_body
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")

    req = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", content_type)
    if token:
        req.add_header("Authorization", token)

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = resp.read()
            status = resp.status
    except urllib.error.HTTPError as e:
        payload = e.read()
        status = e.code

    try:
        return status, json.loads(payload.decode("utf-8"))
    except (ValueError, UnicodeDecodeError):
        return status, payload.decode("utf-8", errors="replace")


def multipart(fields, filename=None, file_content=None, file_type="application/octet-stream"):
    """构造 multipart/form-data 请求体。"""
    boundary = "----kms" + uuid.uuid4().hex
    lines = []
    for key, value in fields.items():
        lines.append(("--" + boundary).encode())
        lines.append(('Content-Disposition: form-data; name="%s"' % key).encode())
        lines.append(b"")
        lines.append(str(value).encode())
    if filename is not None:
        lines.append(("--" + boundary).encode())
        lines.append(
            ('Content-Disposition: form-data; name="file"; filename="%s"' % filename).encode("utf-8"))
        lines.append(("Content-Type: %s" % file_type).encode())
        lines.append(b"")
        lines.append(file_content)
    lines.append(("--" + boundary + "--").encode())
    lines.append(b"")
    return b"\r\n".join(lines), "multipart/form-data; boundary=" + boundary


def check(name, condition, detail=""):
    (PASSED if condition else FAILED).append(name)
    print("  %s %s%s" % ("PASS" if condition else "FAIL", name,
                         ("   -> " + str(detail)) if detail else ""))


def section(title):
    print("\n" + "=" * 72)
    print(title)
    print("=" * 72)


def login(username, password):
    _, resp = request("POST", "/api/auth/login",
                      body={"username": username, "password": password})
    return resp.get("data", {}).get("token")


# ==========================================================================
# A. 认证
# ==========================================================================
def test_auth():
    section("A. 认证")

    status, r = request("GET", "/api/auth/me")
    check("A1 未登录访问 /me 返回 1002", r.get("code") == 1002, r.get("message"))

    status, r = request("POST", "/api/auth/login",
                        body={"username": "admin", "password": "admin123"})
    admin = r.get("data", {}).get("token")
    check("A2 管理员登录成功", r.get("code") == 0 and admin, r.get("message"))
    check("A3 登录返回用户信息与角色",
          r.get("data", {}).get("user", {}).get("role") == "ADMIN",
          r.get("data", {}).get("user"))

    status, r = request("POST", "/api/auth/login",
                        body={"username": "admin", "password": "wrong"})
    check("A4 错误密码返回 2000", r.get("code") == 2000, r.get("message"))

    status, r = request("POST", "/api/auth/login",
                        body={"username": "nobody", "password": "whatever"})
    check("A5 不存在的用户返回与错误密码相同的提示（防用户名枚举）",
          r.get("code") == 2000 and r.get("message") == "用户名或密码错误", r.get("message"))

    status, r = request("POST", "/api/auth/login", body={"username": "", "password": "x"})
    check("A6 空用户名返回 1001 且点明字段",
          r.get("code") == 1001 and "用户名" in r.get("message", ""), r.get("message"))

    status, r = request("GET", "/api/auth/me", token=admin)
    check("A7 带 token 访问 /me 成功",
          r.get("code") == 0 and r.get("data", {}).get("username") == "admin", r.get("data"))

    status, r = request("POST", "/api/auth/logout", token=admin)
    check("A8 登出成功", r.get("code") == 0)

    status, r = request("GET", "/api/auth/me", token=admin)
    check("A9 登出后 token 立即失效", r.get("code") == 1002, r.get("message"))

    return admin


# ==========================================================================
# B. 分类
# ==========================================================================
def test_categories(admin, user):
    section("B. 分类")

    status, r = request("GET", "/api/categories/tree")
    roots = r.get("data", [])
    check("B1 未登录可读取分类树", r.get("code") == 0, r.get("message"))
    check("B2 返回 3 个根分类", len(roots) == 3, [n["name"] for n in roots])
    check("B3 根分类按 sort 排序",
          [n["name"] for n in roots] == ["编程语言", "数据库", "软件工程"],
          [n["name"] for n in roots])

    lang = next((n for n in roots if n["name"] == "编程语言"), {})
    kids = [c["name"] for c in lang.get("children", [])]
    check("B4 三级层级正确", kids == ["Java", "Python"], kids)
    java = next((c for c in lang.get("children", []) if c["name"] == "Java"), {})
    check("B5 第三层（Java 的子分类）",
          [c["name"] for c in java.get("children", [])] == ["Spring", "JVM"],
          [c["name"] for c in java.get("children", [])])

    status, r = request("POST", "/api/categories", body={"name": "test"})
    check("B6 未登录创建分类被拒", r.get("code") == 1002, r.get("message"))

    status, r = request("POST", "/api/categories", token=admin,
                        body={"name": "测试根分类", "sort": 99})
    new_root = r.get("data", {})
    check("B7 创建根分类，level 自动为 1", new_root.get("level") == 1, new_root)

    status, r = request("POST", "/api/categories", token=admin,
                        body={"name": "测试子分类", "parentId": new_root.get("id")})
    new_child = r.get("data", {})
    check("B8 创建子分类，level 自动为父级 +1", new_child.get("level") == 2, new_child)

    status, r = request("POST", "/api/categories", token=admin,
                        body={"name": "孤儿", "parentId": 999999})
    check("B9 父分类不存在被拒", r.get("message") == "父分类不存在", r.get("message"))

    status, r = request("POST", "/api/categories", token=admin,
                        body={"name": "测试根分类"})
    check("B10 同级重名被拒", r.get("message") == "同级下已存在同名分类", r.get("message"))

    status, r = request("PUT", "/api/categories/%d" % new_child["id"], token=admin,
                        body={"name": "测试子分类改名", "parentId": 0})
    updated = r.get("data", {})
    check("B11 改名成功", updated.get("name") == "测试子分类改名", updated.get("name"))
    check("B12 更新时 parentId 被忽略（v0.0.1 不支持移动分类）",
          updated.get("parentId") == new_root.get("id") and updated.get("level") == 2,
          "parentId=%s level=%s" % (updated.get("parentId"), updated.get("level")))

    status, r = request("PUT", "/api/categories/999999", token=admin, body={"name": "x"})
    check("B13 更新不存在的分类被拒", r.get("message") == "分类不存在", r.get("message"))

    status, r = request("DELETE", "/api/categories/1", token=admin)
    check("B14 删除有子分类的分类被拒且说明原因",
          r.get("code") == 2000 and "子分类" in r.get("message", ""), r.get("message"))

    # 必须选一个「无子分类但有条目」的分类，否则会先被子分类规则拦下。
    # seed 中分类 3（Python）无子分类，下挂条目 7、8。
    status, r = request("DELETE", "/api/categories/3", token=admin)
    check("B15 删除有知识条目的分类被拒且给出数量",
          r.get("code") == 2000 and "知识条目" in r.get("message", ""), r.get("message"))

    status, r = request("DELETE", "/api/categories/%d" % new_child["id"], token=admin)
    check("B16 删除空的叶子分类成功", r.get("code") == 0, r.get("message"))

    status, r = request("DELETE", "/api/categories/%d" % new_root["id"], token=admin)
    check("B17 删除已无子分类的父分类成功", r.get("code") == 0, r.get("message"))


# ==========================================================================
# C. 知识条目与状态流转
# ==========================================================================
def test_knowledge(admin, user):
    section("C. 知识条目与状态流转")

    status, r = request("GET", "/api/knowledge", params={"size": 100})
    anon_records = r.get("data", {}).get("records", [])
    anon_total = r.get("data", {}).get("total")
    check("C1 未登录列表只返回已发布条目",
          all(x["status"] == "PUBLISHED" for x in anon_records), "total=%s" % anon_total)
    check("C2 seed 中已发布条目数为 17", anon_total == 17, anon_total)

    status, r = request("GET", "/api/knowledge", params={"mine": "true", "size": 100})
    check("C3 未登录时 mine 参数无效，仍只见已发布",
          r.get("data", {}).get("total") == 17, r.get("data", {}).get("total"))

    status, r = request("GET", "/api/knowledge", token=admin, params={"mine": "true", "size": 100})
    statuses = set(x["status"] for x in r.get("data", {}).get("records", []))
    check("C4 登录后 mine=true 可见自己的全部状态",
          statuses == {"PUBLISHED", "DRAFT", "ARCHIVED"}, statuses)

    status, r = request("POST", "/api/knowledge", body={"title": "x"})
    check("C5 未登录创建条目被拒", r.get("code") == 1002, r.get("message"))

    status, r = request("POST", "/api/knowledge", token=admin, body={"title": ""})
    check("C6 标题为空返回 1001 且点明字段",
          r.get("code") == 1001 and r.get("message") == "标题不能为空", r.get("message"))

    status, r = request("POST", "/api/knowledge", token=admin, body={
        "title": "端到端测试条目",
        "summary": "用于验证全链路的摘要",
        "content": "# 正文\n\n这是端到端测试的正文内容，包含关键词 联调。",
        "categoryId": 2,
        "tagIds": [1, 2],
    })
    item = r.get("data", {})
    item_id = item.get("id")
    check("C7 创建条目成功且初始状态为 DRAFT", item.get("status") == "DRAFT", item.get("status"))
    check("C8 作者自动取当前登录用户", item.get("authorId") == 1, item.get("authorId"))
    check("C9 详情返回分类名", item.get("categoryName") == "Java", item.get("categoryName"))
    check("C10 详情返回作者昵称", item.get("authorName") == "管理员", item.get("authorName"))
    check("C11 详情返回标签名", sorted(item.get("tags", [])) == ["入门", "进阶"], item.get("tags"))

    status, r = request("GET", "/api/knowledge", params={"size": 100})
    check("C12 草稿不出现在未登录列表中",
          r.get("data", {}).get("total") == 17, r.get("data", {}).get("total"))

    status, r = request("GET", "/api/knowledge/%d" % item_id)
    v1 = r.get("data", {}).get("viewCount")
    status, r = request("GET", "/api/knowledge/%d" % item_id)
    v2 = r.get("data", {}).get("viewCount")
    check("C13 详情接口使浏览数自增", v2 == v1 + 1, "%s -> %s" % (v1, v2))

    status, r = request("GET", "/api/knowledge/999999")
    check("C14 不存在的条目返回业务错误而非堆栈",
          r.get("code") == 2000 and r.get("message") == "知识条目不存在", r.get("message"))

    status, r = request("PUT", "/api/knowledge/%d" % item_id, token=user,
                        body={"title": "越权修改"})
    check("C15 非作者修改他人条目被拒",
          r.get("code") == 2000 and r.get("message") == "无权操作他人的知识条目", r.get("message"))

    status, r = request("PUT", "/api/knowledge/%d" % item_id, token=admin,
                        body={"title": "端到端测试条目（已修改）", "categoryId": 2, "tagIds": [3]})
    check("C16 作者修改自己的条目成功",
          r.get("data", {}).get("title") == "端到端测试条目（已修改）", r.get("data", {}).get("title"))
    check("C17 修改标签后详情同步更新", r.get("data", {}).get("tags") == ["面试"],
          r.get("data", {}).get("tags"))

    status, r = request("POST", "/api/knowledge/%d/restore" % item_id, token=admin)
    check("C18 草稿态执行恢复被拒（状态机非法跃迁）",
          r.get("message") == "当前状态不允许恢复", r.get("message"))

    status, r = request("POST", "/api/knowledge/%d/publish" % item_id, token=admin)
    check("C19 草稿发布成功", r.get("data", {}).get("status") == "PUBLISHED",
          r.get("data", {}).get("status"))

    status, r = request("GET", "/api/knowledge", params={"size": 100})
    check("C20 发布后未登录列表可见", r.get("data", {}).get("total") == 18,
          r.get("data", {}).get("total"))

    status, r = request("POST", "/api/knowledge/%d/publish" % item_id, token=admin)
    check("C21 重复发布被拒", r.get("message") == "当前状态不允许发布", r.get("message"))

    status, r = request("POST", "/api/knowledge/%d/archive" % item_id, token=admin)
    check("C22 已发布条目归档成功", r.get("data", {}).get("status") == "ARCHIVED",
          r.get("data", {}).get("status"))

    status, r = request("POST", "/api/knowledge/%d/archive" % item_id, token=admin)
    check("C23 重复归档被拒", r.get("message") == "当前状态不允许归档", r.get("message"))

    status, r = request("POST", "/api/knowledge/%d/publish" % item_id, token=admin)
    check("C24 归档态不能直接发布（必须先恢复）",
          r.get("message") == "当前状态不允许发布", r.get("message"))

    status, r = request("GET", "/api/knowledge", params={"size": 100})
    check("C25 归档后未登录列表不可见", r.get("data", {}).get("total") == 17,
          r.get("data", {}).get("total"))

    status, r = request("POST", "/api/knowledge/%d/restore" % item_id, token=admin)
    check("C26 归档恢复为草稿", r.get("data", {}).get("status") == "DRAFT",
          r.get("data", {}).get("status"))

    status, r = request("DELETE", "/api/knowledge/%d" % item_id, token=user)
    check("C27 非作者删除他人条目被拒", r.get("code") == 2000, r.get("message"))

    return item_id


# ==========================================================================
# D. 分类筛选含子孙
# ==========================================================================
def test_category_filter():
    section("D. 分类筛选（含子孙分类）")

    status, r = request("GET", "/api/knowledge", params={"categoryId": 1, "size": 100})
    parent_total = r.get("data", {}).get("total")
    names = set(x.get("categoryName") for x in r.get("data", {}).get("records", []))
    check("D1 按父分类筛选返回子孙分类的条目",
          parent_total == 7 and names == {"Java", "Python", "Spring", "JVM"},
          "total=%s 分类=%s" % (parent_total, sorted(names)))

    status, r = request("GET", "/api/knowledge", params={"categoryId": 5, "size": 100})
    check("D2 按叶子分类筛选精确匹配", r.get("data", {}).get("total") == 1,
          r.get("data", {}).get("total"))

    # Java(2) 子树 = Java 自身 2 条 + Spring 2 条 + JVM 1 条已发布 = 5
    status, r = request("GET", "/api/knowledge", params={"categoryId": 2, "size": 100})
    check("D3 按中间层分类筛选包含其子树", r.get("data", {}).get("total") == 5,
          r.get("data", {}).get("total"))


# ==========================================================================
# E. 标签
# ==========================================================================
def test_tags():
    section("E. 标签")

    status, r = request("GET", "/api/tags")
    tags = r.get("data", [])
    check("E1 未登录可读取标签列表", r.get("code") == 0 and len(tags) == 5, len(tags))
    check("E2 标签按引用次数降序",
          all(tags[i]["itemCount"] >= tags[i + 1]["itemCount"] for i in range(len(tags) - 1)),
          [(t["name"], t["itemCount"]) for t in tags])
    check("E3 标签含引用次数", all("itemCount" in t for t in tags))


# ==========================================================================
# F. 附件
# ==========================================================================
def test_attachments(admin, user):
    section("F. 附件上传与解析")

    def upload(token, item_id, filename, content, ctype="application/octet-stream", fields=None):
        f = {"itemId": item_id} if fields is None else fields
        body, ct = multipart(f, filename, content, ctype)
        return request("POST", "/api/attachments/upload", token=token,
                       raw_body=body, content_type=ct)

    status, r = upload(None, 1, "a.md", b"x", "text/markdown")
    check("F1 未登录上传被拒", r.get("code") == 1002, r.get("message"))

    status, r = upload(admin, 1, "virus.exe", b"MZ")
    check("F2 可执行扩展名被拒", "不支持的文件类型" in r.get("message", ""), r.get("message"))

    status, r = upload(admin, 1, "noextension", b"x")
    check("F3 无扩展名被拒", "不支持的文件类型" in r.get("message", ""), r.get("message"))

    status, r = upload(user, 1, "a.md", b"x", "text/markdown")
    check("F4 非作者给他人条目挂附件被拒",
          r.get("code") == 2000 and "无权" in r.get("message", ""), r.get("message"))

    md_content = "# 端到端附件\n\n这是用于验证异步解析的正文。\n".encode("utf-8")
    status, r = upload(admin, 1, "端到端附件.md", md_content, "text/markdown")
    md_att = r.get("data", {})
    check("F5 上传 markdown 成功且初始状态为 PENDING",
          r.get("code") == 0 and md_att.get("parseStatus") == "PENDING",
          md_att.get("parseStatus"))

    import time
    time.sleep(3)
    status, r = request("GET", "/api/attachments/%d/text" % md_att["id"], token=admin)
    parsed = r.get("data", {})
    check("F6 异步解析完成，状态流转到 DONE", parsed.get("parseStatus") == "DONE",
          parsed.get("parseStatus"))
    check("F7 抽取文本内容正确（中文完整）",
          "端到端附件" in (parsed.get("textPreview") or "") and "异步解析" in (parsed.get("textPreview") or ""),
          repr(parsed.get("textPreview"))[:60])

    status, r = upload(admin, 1, "legacy.doc", b"legacy doc bytes")
    doc_att = r.get("data", {})
    check("F8 .doc 可上传（能存储能下载）", r.get("code") == 0, r.get("message"))
    time.sleep(2)
    status, r = request("GET", "/api/attachments/%d/text" % doc_att["id"], token=admin)
    check("F9 .doc 解析失败且错误信息点明原因",
          r.get("data", {}).get("parseStatus") == "FAILED"
          and "doc" in (r.get("data", {}).get("parseError") or ""),
          r.get("data", {}).get("parseError"))

    status, r = request("GET", "/api/attachments/item/1", token=admin)
    check("F10 按条目列出附件", len(r.get("data", [])) == 2, len(r.get("data", [])))

    # 下载接口返回的是文件二进制流而非 JSON，因此 r 是 str（UTF-8 解码后）
    status, r = request("GET", "/api/attachments/%d/download" % md_att["id"], token=admin)
    check("F11 下载成功且内容与上传一致",
          status == 200 and isinstance(r, str) and "# 端到端附件" in r,
          "HTTP %s content=%r" % (status, r[:40] if isinstance(r, str) else r))

    status, r = request("GET", "/api/attachments/999999/text", token=admin)
    check("F12 不存在的附件返回业务错误",
          r.get("code") == 2000 and r.get("message") == "附件不存在", r.get("message"))

    status, r = request("DELETE", "/api/attachments/%d" % doc_att["id"], token=user)
    check("F13 非作者删除附件被拒", r.get("code") == 2000, r.get("message"))

    status, r = request("DELETE", "/api/attachments/%d" % doc_att["id"], token=admin)
    check("F14 作者删除附件成功", r.get("code") == 0, r.get("message"))

    # 超过 multipart 上限（spring.servlet.multipart.max-file-size=20MB）
    big = b"0" * (21 * 1024 * 1024)
    status, r = upload(admin, 1, "big.pdf", big, "application/pdf")
    check("F15 超过 20MB 的文件被拒且提示可读",
          r.get("code") != 0 and "20MB" in r.get("message", ""),
          "HTTP %s code=%s msg=%s" % (status, r.get("code"), r.get("message")))


# ==========================================================================
# G. 检索
# ==========================================================================
def test_search(admin):
    section("G. 检索")

    status, r = request("GET", "/api/search", params={"keyword": "索引", "size": 5})
    records = r.get("data", {}).get("records", [])
    check("G1 中文关键词走 ngram 索引并命中",
          r.get("code") == 0 and len(records) > 0, "total=%s" % r.get("data", {}).get("total"))
    check("G2 中文检索首条为标题命中",
          records and "索引" in records[0]["title"], records[0]["title"] if records else None)
    check("G3 中文检索 score 为 TF-IDF 小数且标题命中分值显著更高",
          records and records[0]["score"] > 5, records[0]["score"] if records else None)

    status, r = request("GET", "/api/search", params={"keyword": "Java", "size": 5})
    records = r.get("data", {}).get("records", [])
    check("G4 纯英文关键词走 LIKE 兜底并命中（ngram 不索引拉丁字符）",
          len(records) == 2, "total=%s" % r.get("data", {}).get("total"))
    check("G5 英文检索首条标题含关键词",
          records and "Java" in records[0]["title"], records[0]["title"] if records else None)
    check("G6 英文检索按固定列权重计分（标题 3.0 + 正文 1.0 = 4.0）",
          records and abs(records[0]["score"] - 4.0) < 0.001,
          records[0]["score"] if records else None)

    status, r = request("GET", "/api/search", params={"keyword": "MySQL"})
    check("G7 纯英文技术名词可检索", r.get("data", {}).get("total") == 3,
          r.get("data", {}).get("total"))

    status, r = request("GET", "/api/search", params={"keyword": "zzzznotexist"})
    check("G8 无结果时返回空列表而非报错",
          r.get("code") == 0 and r.get("data", {}).get("total") == 0,
          r.get("data", {}).get("total"))

    status, r = request("GET", "/api/search", params={"keyword": "Java", "categoryId": 2})
    check("G9 关键词 + 分类组合过滤", r.get("data", {}).get("total") == 2,
          r.get("data", {}).get("total"))

    status, r = request("GET", "/api/search", params={"categoryId": 11, "size": 100})
    records = r.get("data", {}).get("records", [])
    check("G10 无关键词时按分类纯过滤，score 全为 0",
          all(x["score"] == 0 for x in records), [x["score"] for x in records])

    status, r = request("GET", "/api/search", params={"categoryId": 11, "sort": "hottest"})
    records = r.get("data", {}).get("records", [])
    check("G11 hottest 排序按浏览数降序",
          [x["title"] for x in records][0] == "MySQL 索引原理",
          [x["title"] for x in records])

    status, r = request("GET", "/api/search", params={"tagId": 5})
    check("G12 按标签过滤", r.get("code") == 0, r.get("data", {}).get("total"))

    status, r = request("GET", "/api/search", params={"keyword": "Java", "status": "PUBLISHED"})
    check("G13 按状态过滤", r.get("data", {}).get("total") == 2, r.get("data", {}).get("total"))

    status, r = request("GET", "/api/search", params={"from": "2020-01-01", "to": "2030-01-01"})
    check("G14 按时间范围过滤", r.get("data", {}).get("total") == 17,
          r.get("data", {}).get("total"))

    status, r = request("GET", "/api/search", params={"keyword": "zzz", "sort": "garbage"})
    check("G15 非法 sort 值回退为 relevance 而不报错", r.get("code") == 0)

    status, r = request("GET", "/api/search", params={"size": 1000, "categoryId": 11})
    check("G16 size 上限被限制为 100", r.get("data", {}).get("size") == 100,
          r.get("data", {}).get("size"))

    status, r = request("GET", "/api/search", params={"mine": "true"})
    check("G17 未登录时 mine 无效，检索不到草稿",
          all(x["status"] == "PUBLISHED" for x in r.get("data", {}).get("records", [])))

    status, r = request("GET", "/api/search", token=admin,
                        params={"keyword": "调优", "mine": "true"})
    statuses = set(x["status"] for x in r.get("data", {}).get("records", []))
    check("G18 登录后 mine=true 可检索到自己的草稿",
          "DRAFT" in statuses, statuses)

    status, r = request("GET", "/api/search", params={"keyword": "索引"})
    check("G19 检索结果带标签",
          all(isinstance(x["tags"], list) for x in r.get("data", {}).get("records", [])))

    status, r = request("GET", "/api/search", params={"keyword": "索引"})
    check("G20 snippet 为纯文本，不含 HTML 标签",
          all("<" not in (x.get("snippet") or "") for x in r.get("data", {}).get("records", [])))


# ==========================================================================
# H. 错误处理
# ==========================================================================
def test_error_handling(admin):
    section("H. 错误处理")

    status, r = request("POST", "/api/knowledge", token=admin, raw_body=b"{not json",
                        content_type="application/json")
    check("H1 畸形 JSON 归为参数错误而非系统异常",
          r.get("code") == 1001 and "请求体格式错误" in r.get("message", ""), r.get("message"))

    status, r = request("PUT", "/api/categories/abc", token=admin, body={"name": "x"})
    check("H2 路径参数类型错误归为参数错误",
          r.get("code") == 1001 and "id" in r.get("message", ""), r.get("message"))

    status, r = request("POST", "/api/attachments/upload", token=admin,
                        raw_body=b"", content_type="application/json")
    check("H3 缺少必填参数返回 1001",
          r.get("code") == 1001, "code=%s msg=%s" % (r.get("code"), r.get("message")))

    gbk = "中文".encode("gbk")
    body = b'{"username":"' + gbk + b'","password":"x"}'
    status, r = request("POST", "/api/auth/login", raw_body=body,
                        content_type="application/json")
    check("H4 非 UTF-8 请求体返回可读提示",
          r.get("code") == 1001, "code=%s msg=%s" % (r.get("code"), r.get("message")))

    status, r = request("GET", "/api/nonexistent/path")
    check("H5 未知路径不返回 500（Spring 默认 404）", status == 404 or r.get("code") != 5000,
          "HTTP %s %s" % (status, str(r)[:60]))


# ==========================================================================
# 主流程
# ==========================================================================
def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except AttributeError:
        pass

    print("知识管理系统 后端端到端联通测试")
    print("目标: %s" % BASE)

    try:
        request("GET", "/api/categories/tree")
    except Exception as e:
        print("\n无法连接后端 %s：%s" % (BASE, e))
        print("请先启动：cd kms-backend && mvn spring-boot:run")
        return 2

    admin = login("admin", "admin123")
    user = login("zhangsan", "user123")
    if not admin or not user:
        print("\n登录失败，请确认数据库已执行 seed.sql")
        return 2

    test_auth()
    test_categories(admin, user)
    test_knowledge(admin, user)
    test_category_filter()
    test_tags()
    test_attachments(admin, user)
    test_search(admin)
    test_error_handling(admin)

    print("\n" + "=" * 72)
    total = len(PASSED) + len(FAILED)
    print("结果: %d/%d 通过" % (len(PASSED), total))
    if FAILED:
        print("\n失败项:")
        for name in FAILED:
            print("  - " + name)
    print("=" * 72)
    return 1 if FAILED else 0


if __name__ == "__main__":
    sys.exit(main())
