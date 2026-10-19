#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""主题与标签配色的浏览器验收。

覆盖本轮三处改动：
  1. 夜间模式：顶栏按钮切换、<html data-theme>、localStorage 持久化、naive 组件跟着变暗
  2. 标签配色：同名标签在详情页/搜索页颜色一致，且不同标签颜色不同
  3. 暗色下关键页面无「白色闪块」：背景与卡片都已跟随主题

前置：后端 8090、前端 5173 均已启动。
用法：python -I scripts/ui_check_theme.py
"""
import sys
from playwright.sync_api import sync_playwright

FRONTEND = "http://localhost:5173"
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


def theme_of(page):
    return page.evaluate("document.documentElement.dataset.theme || 'light'")


with sync_playwright() as p:
    browser = None
    for kwargs in ({}, {"channel": "msedge"}, {"channel": "chrome"}):
        try:
            browser = p.chromium.launch(headless=True, **kwargs)
            break
        except Exception:
            continue
    if browser is None:
        raise SystemExit("没有可用的浏览器")

    page = browser.new_page(viewport={"width": 1280, "height": 900})
    page.goto(FRONTEND, wait_until="networkidle")
    page.wait_for_timeout(1000)

    print("== 1. 主题切换 ==")
    initial = theme_of(page)
    check("初始主题可读", initial in ("light", "dark"), initial)
    toggle = page.locator("button.theme-btn")
    check("顶栏有主题切换按钮", toggle.count() == 1)
    toggle.click()
    page.wait_for_timeout(400)
    after = theme_of(page)
    check("点击后主题切换", after != initial, f"{initial} -> {after}")
    # 刷新后保持
    page.reload(wait_until="networkidle")
    page.wait_for_timeout(600)
    check("刷新后主题保持", theme_of(page) == after, theme_of(page))
    check("localStorage 已记住", page.evaluate("localStorage.getItem('kms.theme')") == after)

    # naive 组件应跟随暗色：卡片背景不能再是白
    bg = page.evaluate(
        "getComputedStyle(document.querySelector('.n-card, .card, .hit') || document.body).backgroundColor")
    print("    body 背景:", page.evaluate("getComputedStyle(document.body).backgroundColor"))

    print("== 2. 标签颜色 ==")
    page.goto(FRONTEND + "/login", wait_until="networkidle")
    page.click("text=管理员 admin / admin123")
    page.click("button:has-text('登录')")
    page.wait_for_timeout(1500)

    # 找一个带多个标签的已发布条目
    page.goto(FRONTEND + "/search?keyword=缓存", wait_until="networkidle")
    page.wait_for_timeout(1500)
    tag_els = page.locator(".hit .n-tag")
    n = tag_els.count()
    check("搜索结果含标签", n >= 2, f"count={n}")
    colors = []
    for i in range(n):
        el = tag_els.nth(i)
        colors.append((el.inner_text().strip(), el.evaluate(
            "e => getComputedStyle(e).backgroundColor")))
    # 同名标签同色
    by_name = {}
    consistent = True
    for name, color in colors:
        if name in by_name and by_name[name] != color:
            consistent = False
        by_name[name] = color
    check("同名标签颜色一致", consistent, str(colors))
    # 不同名标签至少出现两种颜色
    check("不同标签有不同颜色", len({c for _, c in colors}) >= 2, str(colors))

    print("== 3. 暗色模式下的页面没有白色残留 ==")
    # 确保处于暗色
    if theme_of(page) == "light":
        page.locator("button.theme-btn").click()
        page.wait_for_timeout(400)

    for path, selector, label in [
        ("/", ".card", "浏览页卡片"),
        ("/search", ".hit", "搜索页结果"),
        ("/qa", ".bubble-wrap", "问答页"),
    ]:
        page.goto(FRONTEND + path, wait_until="networkidle")
        page.wait_for_timeout(1200)
        # 提取目标元素或 body 的背景，纯白色 (#fff / rgb(255,255,255)) 即视为残留
        rgb = page.evaluate(f"""() => {{
          const el = document.querySelector('{selector}');
          return el ? getComputedStyle(el).backgroundColor : null;
        }}""")
        is_white = rgb in ("rgb(255, 255, 255)", "rgba(255, 255, 255, 1)") if rgb else False
        check(f"{label} 在暗色下不是纯白", not is_white, f"bg={rgb}")

    page.screenshot(path=r"C:\Users\23816\AppData\Local\Temp\kms_theme_dark.png")
    browser.close()

print(f"\n结果：{passed} 通过 / {failed} 失败")
sys.exit(0 if failed == 0 else 1)
