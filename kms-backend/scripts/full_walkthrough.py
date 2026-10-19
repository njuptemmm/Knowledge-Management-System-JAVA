#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
知识管理系统 全功能实测走查。

按设计文档里的功能清单逐项走一遍，每一步都用真实数据（含真实 PDF 附件），
输出每一项的实际结果，而不是只报通过/失败。

与另外三个脚本的分工：
    e2e_smoke.py        功能正确性与边界（99 项）
    e2e_rag.py          智能问答链路（31 项）
    adversarial_test.py 安全性（108 项）
    full_walkthrough.py 本脚本 —— 按功能清单走查，用真实文件，输出可读的过程

前置条件
--------
1. 后端已启动
2. 数据库已重置为 seed 初始状态
3. files/课程考试要求.pdf 存在（真实 PDF 测试数据）

用法
----
    python -I scripts/full_walkthrough.py [base_url]
"""

import io
import json
import os
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8090"
PDF_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "files", "课程考试要求.pdf")

STEPS, FAILED = [], []


def step(name, ok, detail=""):
    STEPS.append((name, ok, detail))
    if not ok:
        FAILED.append(name)
    print("  %s %s" % ("✓" if ok else "✗", name))
    if detail:
        print("      %s" % detail)


def head(title):
    print("\n" + "─" * 74)
    print("  " + title)
    print("─" * 74)


def call(method, path, token=None, body=None, raw=None, ct=None, params=None, timeout=300):
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params, encoding="utf-8")
    data = raw
    if data is None and body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    if ct:
        req.add_header("Content-Type", ct)
    if token:
        req.add_header("Authorization", token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            payload = r.read()
            try:
                return r.status, json.loads(payload.decode("utf-8"))
            except ValueError:
                return r.status, payload
    except urllib.error.HTTPError as e:
        payload = e.read()
        try:
            return e.code, json.loads(payload.decode("utf-8"))
        except ValueError:
            return e.code, payload


def upload_pdf(token, item_id, path, filename):
    content = io.open(path, "rb").read()
    boundary = "----walk" + str(int(time.time() * 1000))
    parts = []
    for k, v in {"itemId": item_id}.items():
        parts += [("--" + boundary).encode(),
                  ('Content-Disposition: form-data; name="%s"' % k).encode(),
                  b"", str(v).encode()]
    parts += [("--" + boundary).encode(),
              ('Content-Disposition: form-data; name="file"; filename="%s"' % filename).encode("utf-8"),
              b"Content-Type: application/pdf", b"", content,
              ("--" + boundary + "--").encode(), b""]
    body = b"\r\n".join(parts)
    return call("POST", "/api/attachments/upload", token=token, raw=body,
                ct="multipart/form-data; boundary=" + boundary)


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except AttributeError:
        pass

    print("=" * 74)
    print("  知识管理系统 全功能实测走查")
    print("  目标: %s" % BASE)
    print("=" * 74)

    if not os.path.exists(PDF_PATH):
        print("找不到测试 PDF: %s" % PDF_PATH)
        return 2
    pdf_size = os.path.getsize(PDF_PATH)
    print("\n真实测试数据: %s (%.0f KB)" % (os.path.basename(PDF_PATH), pdf_size / 1024))

    # ---------------------------------------------------------------- 1
    head("1. 用户与认证")
    _, r = call("POST", "/api/auth/login", body={"username": "admin", "password": "admin123"})
    admin = r.get("data", {}).get("token")
    step("管理员登录", bool(admin), "角色 %s" % r.get("data", {}).get("user", {}).get("role"))

    _, r = call("POST", "/api/auth/login", body={"username": "user", "password": "user123"})
    user = r.get("data", {}).get("token")
    step("普通用户登录", bool(user), "昵称 %s" % r.get("data", {}).get("user", {}).get("nickname"))

    _, r = call("GET", "/api/auth/me", token=admin)
    step("查询当前用户", r.get("data", {}).get("username") == "admin")

    _, r = call("POST", "/api/auth/logout", token=admin)
    step("登出", r.get("code") == 0)
    _, r = call("GET", "/api/auth/me", token=admin)
    step("登出后令牌失效", r.get("code") == 1002, r.get("message"))
    _, r = call("POST", "/api/auth/login", body={"username": "admin", "password": "admin123"})
    admin = r["data"]["token"]

    # ---------------------------------------------------------------- 2
    head("2. 分类树（知识库的层次结构）")
    _, r = call("GET", "/api/categories/tree")
    roots = r["data"]
    step("读取分类树", len(roots) == 3, "根分类: %s" % [x["name"] for x in roots])

    def depth(nodes):
        # 空列表深度为 0。用 max(..., default=0) 会让叶子节点也算成 1，整体多算一级
        if not nodes:
            return 0
        return 1 + max(depth(n["children"]) for n in nodes)

    step("层级深度", depth(roots) == 3,
         "共 %d 级（seed 中 编程语言 > Java > Spring/JVM）" % depth(roots))

    _, r = call("POST", "/api/categories", token=admin, body={"name": "实测分类", "parentId": 1})
    c1 = r["data"]["id"]
    step("新建分类自动推导层级", r["data"]["level"] == 2, "level=%s" % r["data"]["level"])

    _, r = call("POST", "/api/categories", token=admin, body={"name": "实测子分类", "parentId": c1})
    c2 = r["data"]["id"]
    step("新建二级子分类", r["data"]["level"] == 3, "level=%s" % r["data"]["level"])

    _, r = call("PUT", "/api/categories/%d" % c1, token=admin,
                body={"name": "实测分类（改名）", "description": "走查用"})
    step("修改分类", r["data"]["name"] == "实测分类（改名）")

    _, r = call("DELETE", "/api/categories/%d" % c1, token=admin)
    step("删除有子分类的分类被拒", r["code"] == 2000, r["message"])
    call("DELETE", "/api/categories/%d" % c2, token=admin)
    call("DELETE", "/api/categories/%d" % c1, token=admin)

    # ---------------------------------------------------------------- 3
    head("3. 知识条目（CMS：创建 / 编辑 / 发布 / 归档）")
    _, r = call("POST", "/api/knowledge", token=admin, body={
        "title": "走查用知识条目",
        "summary": "全功能实测走查时创建的条目",
        "content": "# 走查内容\n\n这段正文用于验证创建、检索与问答链路。\n\n"
                   "## 子章节\n\n包含唯一标记词 走查标识甲，便于检索验证。",
        "categoryId": 2,
        "tagIds": [1, 2],
    })
    item = r["data"]
    item_id = item["id"]
    step("创建条目（草稿）", item["status"] == "DRAFT",
         "id=%s 作者=%s 分类=%s 标签=%s" % (item_id, item["authorName"],
                                            item["categoryName"], item["tags"]))

    _, r = call("GET", "/api/knowledge", params={"size": 100})
    step("未登录列表看不到草稿", all(x["id"] != item_id for x in r["data"]["records"]),
         "可见条目 %d 条" % r["data"]["total"])

    _, r = call("GET", "/api/knowledge", token=admin, params={"mine": "true", "size": 100})
    step("作者本人能看到自己的草稿",
         any(x["id"] == item_id for x in r["data"]["records"]))

    _, r = call("GET", "/api/knowledge/%d" % item_id)
    v1 = r["data"]["viewCount"]
    _, r = call("GET", "/api/knowledge/%d" % item_id)
    step("浏览数自增", r["data"]["viewCount"] == v1 + 1, "%s → %s" % (v1, r["data"]["viewCount"]))

    _, r = call("PUT", "/api/knowledge/%d" % item_id, token=user, body={"title": "越权"})
    step("他人无法修改（越权拦截）", r["code"] == 2000, r["message"])

    _, r = call("PUT", "/api/knowledge/%d" % item_id, token=admin,
                body={"title": "走查用知识条目（已编辑）", "categoryId": 2, "tagIds": [3]})
    step("作者编辑条目", r["data"]["title"] == "走查用知识条目（已编辑）",
         "标签已更新为 %s" % r["data"]["tags"])

    _, r = call("POST", "/api/knowledge/%d/publish" % item_id, token=admin)
    step("发布", r["data"]["status"] == "PUBLISHED")

    _, r = call("GET", "/api/knowledge", params={"size": 100})
    step("发布后未登录可见", any(x["id"] == item_id for x in r["data"]["records"]),
         "可见条目 %d 条" % r["data"]["total"])

    _, r = call("POST", "/api/knowledge/%d/archive" % item_id, token=admin)
    step("归档", r["data"]["status"] == "ARCHIVED")
    _, r = call("POST", "/api/knowledge/%d/restore" % item_id, token=admin)
    step("从归档恢复", r["data"]["status"] == "DRAFT")
    call("POST", "/api/knowledge/%d/publish" % item_id, token=admin)

    # ---------------------------------------------------------------- 4
    head("4. 附件处理（真实 PDF：上传 → 解析 → 下载比对）")
    st, r = upload_pdf(admin, item_id, PDF_PATH, "课程考试要求.pdf")
    att = r.get("data", {})
    att_id = att.get("id")
    step("上传真实 PDF", r.get("code") == 0,
         "%s %.0f KB 状态=%s" % (att.get("originalName"), att.get("sizeBytes", 0) / 1024,
                                 att.get("parseStatus")))

    parsed = None
    for _ in range(30):
        time.sleep(1)
        _, t = call("GET", "/api/attachments/%d/text" % att_id, token=admin)
        parsed = t["data"]
        if parsed["parseStatus"] in ("DONE", "FAILED"):
            break
    step("异步解析完成", parsed["parseStatus"] == "DONE", "状态=%s 错误=%s"
         % (parsed["parseStatus"], parsed["parseError"]))

    preview = parsed.get("textPreview") or ""
    step("抽取到中文文本", len(preview) > 50, "预览前 60 字: %s" % preview[:60].replace("\r\n", " "))

    # 关键验证：PDF 内嵌字体常把汉字映射成康熙部首，归一化后应能按标准汉字匹配
    radicals = [c for c in preview if 0x2E80 <= ord(c) <= 0x2FDF]
    step("抽取文本已归一化为标准汉字（无康熙部首残留）", not radicals,
         "残留兼容字符: %s" % (radicals if radicals else "无"))

    step("可用标准汉字检索到抽取内容", "工程" in preview or "软件" in preview,
         "「工程」出现: %s" % ("工程" in preview))

    st, body = call("GET", "/api/attachments/%d/download" % att_id, token=admin)
    original = io.open(PDF_PATH, "rb").read()
    step("下载文件与上传字节一致", body == original,
         "下载 %d 字节 / 原文件 %d 字节" % (len(body), len(original)))

    _, r = call("GET", "/api/attachments/item/%d" % item_id, token=admin)
    step("按条目列出附件", any(a["id"] == att_id for a in r["data"]),
         "共 %d 个附件" % len(r["data"]))

    # ---------------------------------------------------------------- 5
    head("5. 标签")
    _, r = call("GET", "/api/tags")
    tags = r["data"]
    step("标签列表含引用次数", len(tags) == 5 and all("itemCount" in t for t in tags),
         ", ".join("%s(%d)" % (t["name"], t["itemCount"]) for t in tags))

    # ---------------------------------------------------------------- 6
    head("6. 检索（全文 + 高级筛选）")
    _, r = call("GET", "/api/search", params={"keyword": "索引", "size": 3})
    recs = r["data"]["records"]
    step("中文检索（ngram 全文索引）", len(recs) > 0,
         "命中 %d 条，首条《%s》相关度 %.3f" % (r["data"]["total"], recs[0]["title"], recs[0]["score"]))

    _, r = call("GET", "/api/search", params={"keyword": "Java", "size": 3})
    recs = r["data"]["records"]
    step("英文检索（LIKE 兜底）", len(recs) > 0,
         "命中 %d 条，首条《%s》" % (r["data"]["total"], recs[0]["title"]))

    _, r = call("GET", "/api/search", params={"keyword": "走查标识甲"})
    step("能检索到本次新建条目的正文", r["data"]["total"] >= 1,
         "命中 %d 条" % r["data"]["total"])

    _, r = call("GET", "/api/search", params={"categoryId": 11, "size": 100})
    step("按分类筛选（含子孙分类）", r["data"]["total"] > 0,
         "MySQL 分类下 %d 条" % r["data"]["total"])

    _, r = call("GET", "/api/search", params={"tagId": 3, "size": 100})
    step("按标签筛选", r["code"] == 0, "命中 %d 条" % r["data"]["total"])

    _, r = call("GET", "/api/search", params={"categoryId": 11, "sort": "hottest", "size": 3})
    step("按热度排序", r["code"] == 0,
         " → ".join(x["title"] for x in r["data"]["records"]))

    # ---------------------------------------------------------------- 7
    head("7. 智能问答（RAG）")
    _, r = call("GET", "/api/qa/status")
    enabled = r["data"]["enabled"]
    step("问答功能状态", True, "enabled=%s 已索引 %s 条目 / %s 块"
         % (enabled, r["data"]["indexedItems"], r["data"]["indexedChunks"]))

    if enabled:
        t0 = time.time()
        _, r = call("POST", "/api/qa/reindex", token=admin)
        step("全量重建索引", r["code"] == 0,
             "%s 条目 / %s 块，耗时 %.1f 秒" % (r["data"]["indexedItems"],
                                                r["data"]["indexedChunks"], time.time() - t0))

        def ask(question, chat_id=None):
            body = {"question": question}
            if chat_id:
                body["chatId"] = chat_id
            req = urllib.request.Request(BASE + "/api/qa/ask",
                                         data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
                                         method="POST")
            req.add_header("Content-Type", "application/json")
            req.add_header("Accept", "text/event-stream")
            srcs, ans, err, ev = [], [], None, None
            with urllib.request.urlopen(req, timeout=300) as resp:
                for raw in resp:
                    line = raw.decode("utf-8").rstrip("\n")
                    if line.startswith("event:"):
                        ev = line[6:].strip()
                    elif line.startswith("data:"):
                        d = line[5:].strip()
                        try:
                            p = json.loads(d)
                        except ValueError:
                            p = d
                        if ev == "sources":
                            srcs = p
                        elif ev == "delta":
                            ans.append(p if isinstance(p, str) else str(p))
                        elif ev == "error":
                            err = p.get("message") if isinstance(p, dict) else str(p)
            return srcs, "".join(ans), err

        srcs, ans, err = ask("MySQL 索引用了什么数据结构？")
        step("知识库内的问题能被回答", bool(ans) and "知识库中" not in ans,
             "引用 %d 条，首条《%s》" % (len(srcs), srcs[0]["title"] if srcs else "无"))
        print("      答案: %s" % ans[:150].replace("\n", " "))

        srcs, ans, err = ask("今天上海天气怎么样？")
        step("知识库外的问题被拒答", "知识库中" in ans, "答案: %s" % ans[:60])

        srcs, ans, err = ask("怎么用 jmap 定位内存泄漏？")
        leaked = any("调优实战" in s["title"] for s in srcs)
        step("草稿内容不会通过问答泄露", not leaked,
             "引用: %s" % [s["title"] for s in srcs][:3])

        chat_id = "walkthrough-" + str(int(time.time()))
        ask("Redis 有哪几种数据结构？", chat_id)
        _, r = call("GET", "/api/qa/history/" + chat_id)
        step("会话历史记录", len(r["data"]) == 2, "%d 条消息" % len(r["data"]))
        call("DELETE", "/api/qa/history/" + chat_id)
    else:
        step("问答功能未启用（未配置密钥）", True, "跳过 RAG 相关验证")

    # ---------------------------------------------------------------- 8
    head("8. 权限与可见性")
    _, r = call("GET", "/api/categories/tree")
    step("未登录可浏览分类", r["code"] == 0)
    _, r = call("GET", "/api/knowledge", params={"size": 5})
    step("未登录可浏览已发布条目", r["code"] == 0 and r["data"]["total"] > 0,
         "%d 条" % r["data"]["total"])
    _, r = call("POST", "/api/knowledge", body={"title": "匿名"})
    step("未登录不能创建", r["code"] == 1002, r["message"])
    _, r = call("DELETE", "/api/knowledge/1", token=user)
    step("普通用户不能删他人条目", r["code"] == 2000, r["message"])

    # ---------------------------------------------------------------- 9
    head("9. 清理")
    _, r = call("DELETE", "/api/attachments/%d" % att_id, token=admin)
    step("删除附件", r["code"] == 0)
    _, r = call("DELETE", "/api/knowledge/%d" % item_id, token=admin)
    step("删除走查条目", r["code"] == 0)

    # ---------------------------------------------------------------- 汇总
    print("\n" + "=" * 74)
    ok = sum(1 for _, passed, _ in STEPS if passed)
    print("  结果: %d/%d 项通过" % (ok, len(STEPS)))
    if FAILED:
        print("\n  失败项:")
        for n in FAILED:
            print("    - " + n)
    print("=" * 74)
    return 1 if FAILED else 0


if __name__ == "__main__":
    sys.exit(main())
