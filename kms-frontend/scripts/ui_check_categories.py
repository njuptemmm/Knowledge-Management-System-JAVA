#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""分类管理页的浏览器验收。

覆盖计划 F7 声明的能力，以及本轮修掉的两个「看起来没实现」问题：
  1. 进入页面默认选中第一个分类，右侧直接是编辑表单，而不是空提示
  2. 窄屏（900px）侧栏仍然可见，分类树与新建入口都点得到
另外验证新建根分类 / 新建子分类 / 删除保护 / 删除。

前置：后端 8090、前端 5173 均已启动，admin/admin123 可登录。
用法：python -I scripts/ui_check_categories.py
"""

import sys
import time

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


def select_node(page, text):
    node = page.locator(f".n-tree-node-content:has-text('{text}')").first
    node.click()
    page.wait_for_timeout(400)


def confirm_delete(page):
    page.locator(".n-dialog button:has-text('删除')").first.click()
    page.wait_for_timeout(1200)


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
    page.goto(FRONTEND + "/login", wait_until="networkidle")
    page.click("text=管理员 admin / admin123")
    page.click("button:has-text('登录')")
    page.wait_for_timeout(1500)

    print("== 1. 进入页面即有内容 ==")
    page.goto(FRONTEND + "/categories", wait_until="networkidle")
    page.wait_for_timeout(1500)
    check("标题是分类管理", page.locator("h2:has-text('分类管理')").count() == 1)
    check("分类树已渲染", page.locator(".n-tree-node").count() >= 9,
          f"nodes={page.locator('.n-tree-node').count()}")
    check("默认选中第一个分类", page.locator(".n-tree-node--selected").count() >= 1)
    form = page.locator(".form-card")
    check("右侧直接显示编辑表单", form.count() == 1 and "编辑分类" in form.inner_text(),
          form.inner_text()[:60] if form.count() else "no form")
    name_value = page.locator("input[placeholder='分类名称']").input_value()
    check("表单带出分类名称", bool(name_value), name_value)

    print("== 2. 新建根分类 ==")
    unique = "临时根%d" % int(time.time())
    page.click("text=+ 新建根分类")
    page.wait_for_timeout(400)
    check("进入新建根分类模式", page.locator("text=新建根分类").count() >= 1)
    page.fill("input[placeholder='分类名称']", unique)
    page.click("button:has-text('创建')")
    page.wait_for_timeout(1500)
    check("新根分类出现在树中", page.locator(f"text={unique}").count() >= 1)
    check("创建后自动切到该分类", page.locator("input[placeholder='分类名称']").input_value() == unique,
          page.locator("input[placeholder='分类名称']").input_value())

    print("== 3. 新建子分类 ==")
    child = "临时子%d" % int(time.time())
    page.click("text=+ 新建子分类")
    page.wait_for_timeout(400)
    check("进入新建子分类模式", page.locator(".form-head:has-text('新建子分类')").count() == 1,
          page.locator(".form-head").inner_text() if page.locator(".form-head").count() else "")
    page.fill("input[placeholder='分类名称']", child)
    page.click("button:has-text('创建')")
    page.wait_for_timeout(1500)
    check("子分类出现在树中", page.locator(f"text={child}").count() >= 1)

    print("== 4. 删除保护 ==")
    select_node(page, "编程语言")
    page.click("button:has-text('删除')")
    page.wait_for_timeout(400)
    confirm_delete(page)
    body = page.locator("body").inner_text()
    check("删除有子分类的分类被拒并说明原因",
          "子分类" in body or "无法删除" in body or "请先" in body)

    print("== 5. 删除自己建的两个分类（清理） ==")
    select_node(page, child)
    page.click("button:has-text('删除')")
    page.wait_for_timeout(400)
    confirm_delete(page)
    check("子分类已删除", page.locator(f"text={child}").count() == 0)

    select_node(page, unique)
    page.click("button:has-text('删除')")
    page.wait_for_timeout(400)
    confirm_delete(page)
    check("根分类已删除", page.locator(f"text={unique}").count() == 0)

    print("== 6. 窄屏侧栏保留 ==")
    page.set_viewport_size({"width": 900, "height": 900})
    page.wait_for_timeout(600)
    check("窄屏侧栏仍可见", page.locator("aside.sidebar").is_visible())
    check("窄屏仍能看到分类树", page.locator(".n-tree-node").count() >= 9)
    check("窄屏仍能点新建根分类", page.locator("text=+ 新建根分类").count() >= 1)

    browser.close()

print(f"\n结果：{passed} 通过 / {failed} 失败")
sys.exit(0 if failed == 0 else 1)
