#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""智能问答会话侧栏 + 搜索框去重的浏览器验收。

只验证本轮改动的三件事，不重复 test_frontend.py 的全量覆盖：
  1. /search 页面上只剩一个搜索输入框（顶栏那个已隐藏）
  2. /qa 登录后出现「历史会话」侧栏，且能看到后端返回的会话
  3. 点击会话能加载历史消息

前置：后端 8090、前端 5173 均已启动，账号 user/user123 可登录。

用法：python -I scripts/ui_check_sessions.py
"""

import json
import sys
import urllib.request

from playwright.sync_api import sync_playwright

FRONTEND = "http://localhost:5173"
BACKEND = "http://localhost:8090"

passed = 0
failed = 0


def check(name, cond, detail=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"  PASS  {name}")
    else:
        failed += 1
        print(f"  FAIL  {name}  {detail}")


def api_login(username, password):
    req = urllib.request.Request(
        BACKEND + "/api/auth/login",
        data=json.dumps({"username": username, "password": password}).encode("utf-8"),
        method="POST",
    )
    req.add_header("Content-Type", "application/json; charset=utf-8")
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))["data"]["token"]


def api_ask(token, question):
    """直接问一次，制造一条真实会话，供 UI 列表验证。"""
    req = urllib.request.Request(
        BACKEND + "/api/qa/ask",
        data=json.dumps({"question": question}, ensure_ascii=False).encode("utf-8"),
        method="POST",
    )
    req.add_header("Content-Type", "application/json; charset=utf-8")
    req.add_header("Accept", "text/event-stream")
    req.add_header("Authorization", token)
    # 读完整个流，确保后端在 doOnComplete 里把助手消息写进历史
    with urllib.request.urlopen(req, timeout=180) as resp:
        resp.read()
    return None


print("== 准备：通过 API 建一条会话 ==")
token = api_login("user", "user123")
question = "ArrayList 和 LinkedList 有什么不同？"
api_ask(token, question)
print("  已提问：", question)

def launch(p):
    """优先用 Playwright 自带的 Chromium；没装浏览器时退回系统 Edge/Chrome。

    这台机器只装了 playwright 包、没下浏览器二进制，而系统 Edge 就能跑同样的检查，
    不值得为一次冒烟测试再拉 150MB。
    """
    for kwargs in ({}, {"channel": "msedge"}, {"channel": "chrome"}):
        try:
            return p.chromium.launch(headless=True, **kwargs)
        except Exception:
            continue
    raise SystemExit("没有可用的浏览器：请运行 python -m playwright install chromium")


with sync_playwright() as p:
    browser = launch(p)
    page = browser.new_page(viewport={"width": 1280, "height": 900})
    page.goto(FRONTEND + "/login", wait_until="networkidle")
    page.click("text=普通用户 user / user123")
    page.click("button:has-text('登录')")
    page.wait_for_timeout(1500)
    check("登录成功（顶栏出现用户名）", page.locator("text=user").first.is_visible())

    print("== 1. 搜索页只有一个搜索框 ==")
    page.goto(FRONTEND + "/search", wait_until="networkidle")
    page.wait_for_timeout(800)
    top_search = page.locator("input[placeholder*='搜索知识库']").count()
    page_search = page.locator("input[placeholder*='关键词']").count()
    check("顶栏搜索框已隐藏", top_search == 0, f"count={top_search}")
    check("页面内搜索框保留", page_search == 1, f"count={page_search}")

    print("== 2. 问答页会话侧栏 ==")
    page.goto(FRONTEND + "/qa", wait_until="networkidle")
    page.wait_for_timeout(1500)
    check("侧栏标题可见", page.locator("text=历史会话").count() == 1)
    check("新建会话按钮可见", page.locator("text=+ 新建会话").count() >= 1)
    session_item = page.locator(".session-item").first
    check("列表中有会话", page.locator(".session-item").count() >= 1,
          f"count={page.locator('.session-item').count()}")
    check("会话标题取自首句提问", "ArrayList" in session_item.inner_text(),
          session_item.inner_text() if page.locator(".session-item").count() else "")

    print("== 3. 点击会话加载历史 ==")
    session_item.click()
    page.wait_for_timeout(1200)
    check("历史消息已渲染", page.locator(".msg.user").count() >= 1,
          f"user msgs={page.locator('.msg.user').count()}")
    check("答案气泡已渲染", page.locator(".msg.assistant .bubble").count() >= 1)

    print("== 4. 新建会话真的清空并给出反馈 ==")
    page.locator("text=+ 新建会话").first.click()
    page.wait_for_timeout(600)
    check("消息已清空", page.locator(".msg").count() == 0,
          f"msg={page.locator('.msg').count()}")
    check("当前会话高亮已取消", page.locator(".session-item.active").count() == 0)
    check("出现新会话提示", page.locator("text=已开始新会话").count() >= 1)

    print("== 5. 窄屏仍有会话入口 ==")
    page.set_viewport_size({"width": 900, "height": 900})
    page.wait_for_timeout(600)
    check("窄屏侧栏已隐藏", page.locator("aside.sidebar").is_visible() is False)
    check("窄屏页头出现新建会话", page.locator("text=+ 新建会话").count() >= 1)
    check("窄屏页头出现历史会话入口", page.locator("text=历史会话（").count() >= 1)
    page.locator("text=历史会话（").click()
    page.wait_for_timeout(400)
    check("窄屏下拉能列出会话", page.locator(".n-dropdown-option").count() >= 1,
          f"options={page.locator('.n-dropdown-option').count()}")
    page.set_viewport_size({"width": 1280, "height": 900})

    browser.close()

print(f"\n结果：{passed} 通过 / {failed} 失败")
sys.exit(0 if failed == 0 else 1)
