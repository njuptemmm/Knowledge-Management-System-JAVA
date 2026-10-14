#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
知识管理系统 前端端到端测试（Playwright 驱动真实浏览器）。

它验证的是「构建与类型检查都发现不了」的那类问题：
组件没注册导致整页空白、模板里写了不存在的字段、交互流程走不通、
XSS 载荷被当成 HTML 执行等。

前置条件
--------
1. 后端已启动（http://localhost:8090）
2. 前端开发服务器已启动（http://localhost:5173）
3. 数据库已执行 seed.sql

用法
----
    python -I scripts/e2e_frontend.py
"""

import os
import sys
import time

from playwright.sync_api import sync_playwright

BASE = "http://localhost:5173"
BACKEND = "http://localhost:8090"
SHOT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".tmp", "e2e")

PASSED, FAILED = [], []


def check(name, ok, detail=""):
    (PASSED if ok else FAILED).append(name)
    print("  %s %s%s" % ("PASS" if ok else "FAIL", name, ("   -> " + str(detail)) if detail else ""))


def section(title):
    print("\n" + "=" * 74)
    print(title)
    print("=" * 74)


def launch_browser(p):
    """
    按可用性选择浏览器。

    本机没装 playwright 自带的 chromium（只有指向不存在的路径），
    但系统装了 Edge，因此优先用已装的浏览器，避免为此额外下载上百 MB。
    """
    errors = []
    for channel in ("msedge", "chrome", None):
        try:
            browser = p.chromium.launch(headless=True, channel=channel)
            print("浏览器: %s" % (channel or "playwright 自带 chromium"))
            return browser
        except Exception as e:
            errors.append("%s: %s" % (channel, str(e).splitlines()[0][:80]))
    raise RuntimeError("找不到可用浏览器\n  " + "\n  ".join(errors))


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except AttributeError:
        pass

    os.makedirs(SHOT_DIR, exist_ok=True)
    print("前端端到端测试")
    print("前端: %s" % BASE)
    print("后端: %s" % BACKEND)

    console_errors = []
    page_errors = []

    with sync_playwright() as p:
        browser = launch_browser(p)
        context = browser.new_context(viewport={"width": 1440, "height": 1000}, locale="zh-CN")
        page = context.new_page()

        # 收集控制台错误与未捕获异常。Vue 的「无法解析组件」警告会在这里出现
        page.on("console", lambda msg: console_errors.append(msg.text)
                if msg.type in ("error", "warning") else None)
        page.on("pageerror", lambda err: page_errors.append(str(err)))

        def shot(name):
            page.screenshot(path=os.path.join(SHOT_DIR, name + ".png"), full_page=True)

        def goto(path, wait=1200):
            page.goto(BASE + path)
            page.wait_for_timeout(wait)

        # ---------------------------------------------------------------- 1
        section("1. 匿名浏览")
        goto("/")
        check("首页可访问", page.locator("text=知识管理系统").count() > 0)
        cards = page.locator(".card")
        check("列表渲染出条目", cards.count() > 0, "%d 张卡片" % cards.count())
        check("分类树渲染", page.locator(".category-tree").count() > 0)
        check("树中包含「编程语言」", page.locator(".category-tree >> text=编程语言").count() > 0)
        shot("01-browse")

        # 点分类筛选
        page.locator(".category-tree >> text=Java").first.click()
        page.wait_for_timeout(1200)
        filtered = page.locator(".card").count()
        check("点击分类后列表变化", filtered > 0, "%d 张卡片" % filtered)

        # ---------------------------------------------------------------- 2
        section("2. 检索与高亮")
        goto("/search?keyword=%E7%B4%A2%E5%BC%95")
        check("检索结果渲染", page.locator(".hit").count() > 0,
              "%d 条结果" % page.locator(".hit").count())
        marks = page.locator(".hit mark")
        check("关键词被高亮", marks.count() > 0, "%d 处高亮" % marks.count())
        # 高亮必须是模板渲染出来的 <mark>，而不是把 HTML 塞进页面
        body_html = page.content()
        check("页面未把用户输入当 HTML 注入", "<script>alert" not in body_html)
        shot("02-search")

        # ---------------------------------------------------------------- 3
        section("3. XSS 防御")
        goto("/search?keyword=%3Cscript%3Ealert(1)%3C%2Fscript%3E")
        page.wait_for_timeout(800)
        check("XSS 载荷未执行", len(page_errors) == 0, page_errors[:1])

        # ---------------------------------------------------------------- 4
        section("4. 登录")
        goto("/login")
        check("登录页渲染", page.locator("text=测试账号").count() > 0)
        shot("03-login")

        page.fill('input[autocomplete="username"]', "admin")
        page.fill('input[autocomplete="current-password"]', "admin123")
        page.click("button:has-text('登录')")
        page.wait_for_timeout(2000)
        check("登录成功并跳转", "/login" not in page.url, page.url)
        check("顶栏显示用户名", page.locator("text=管理员").count() > 0)

        # ---------------------------------------------------------------- 5
        section("5. 需登录页面")
        goto("/mine")
        check("我的知识库可访问", page.locator("text=我的知识库").count() > 0)
        check("三个状态页签存在",
              all(page.locator("text=" + t).count() > 0 for t in ["草稿", "已发布", "已归档"]))
        shot("04-mine")

        goto("/editor/new")
        check("编辑器可访问", page.locator("text=新建知识条目").count() > 0)
        check("有 Markdown 预览区", page.locator("text=预览").count() > 0)
        shot("05-editor")

        # ---------------------------------------------------------------- 6
        section("6. 通过界面创建一条知识")
        unique = "前端E2E验证条目-" + str(int(time.time()))
        page.fill('input[placeholder="给这篇知识起个名字"]', unique)
        page.fill('textarea[placeholder^="一句话概括"]', "由前端端到端测试创建，用于验证完整链路")
        page.fill('textarea[placeholder^="# 标题"]', "# 端到端验证\n\n这段正文包含唯一标记 前端E2E标记甲。\n\n## 子章节\n\n用于验证检索。")
        page.wait_for_timeout(500)
        check("Markdown 实时预览生效",
              page.locator(".preview >> text=子章节").count() > 0)
        shot("06-editor-filled")

        page.click("button:has-text('保存并发布')")
        page.wait_for_timeout(2500)
        check("保存后跳转到详情页", "/detail/" in page.url, page.url)
        check("详情页显示新建的标题", page.locator("text=" + unique).count() > 0)
        check("正文 Markdown 已渲染", page.locator(".markdown-body h1").count() > 0)
        shot("07-detail-new")

        # 记下 id，稍后清理
        created_url = page.url

        # ---------------------------------------------------------------- 7
        section("7. 附件上传与解析（真实 PDF）")
        pdf = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "files", "课程考试要求.pdf"))
        if os.path.exists(pdf):
            page.set_input_files('input[type="file"]', pdf)
            page.wait_for_timeout(1000)
            check("上传后出现进度状态",
                  page.locator("text=等待解析").count() + page.locator("text=解析中").count() > 0
                  or page.locator("text=解析完成").count() > 0)
            # 等解析完成，最多 40 秒
            for _ in range(40):
                if page.locator("text=解析完成").count() > 0:
                    break
                page.wait_for_timeout(1000)
            done = page.locator("text=解析完成").count() > 0
            check("PDF 解析完成", done)
            if done:
                page.locator("summary:has-text('查看抽取的文本')").first.click()
                page.wait_for_timeout(500)
                preview = page.locator(".preview pre").first.inner_text()
                check("抽取到中文文本", len(preview) > 50, preview[:50].replace("\n", " "))
                check("抽取文本已归一化（含标准汉字「工程」）", "工程" in preview)
            shot("08-attachment")
        else:
            check("找到测试 PDF", False, pdf)

        # ---------------------------------------------------------------- 8
        section("8. 智能问答")
        goto("/qa", wait=2000)
        check("问答页渲染", page.locator("text=智能问答").count() > 0)
        enabled = page.locator("text=问答功能未配置").count() == 0
        check("问答功能已启用", enabled)
        if enabled:
            page.fill("textarea[placeholder^='输入你的问题']", "MySQL 索引用了什么数据结构？")
            submit_at = time.time()
            page.keyboard.press("Enter")

            # 记录引用与答案各自第一次出现的时间。
            # 检索要几秒，不能只等固定时长就断言——那样测的是网速不是实现。
            sources_at = None
            answer_at = None
            for _ in range(100):          # 最多等约 30 秒
                if sources_at is None and page.locator(".sources").count() > 0:
                    sources_at = time.time() - submit_at
                if answer_at is None:
                    # 判定「答案文本已开始出现」要排除两个干扰：
                    # 1) 用户自己的提问气泡（同样是 .bubble）
                    # 2) 占位文案「正在检索并生成…」——它在检索期间就存在
                    bubbles = page.locator(".msg.assistant .bubble")
                    if bubbles.count() > 0:
                        text = bubbles.last.inner_text().strip()
                        if len(text) > 5 and "正在检索" not in text:
                            answer_at = time.time() - submit_at
                if sources_at is not None and answer_at is not None:
                    break
                page.wait_for_timeout(300)

            check("引用来源已渲染", sources_at is not None,
                  "%.1fs" % sources_at if sources_at else "未出现")
            check("答案已生成", answer_at is not None,
                  "%.1fs" % answer_at if answer_at else "未出现")
            if sources_at is not None and answer_at is not None:
                check("引用先于答案出现（用户先看到找到了什么）", sources_at <= answer_at,
                      "引用 %.1fs / 答案 %.1fs" % (sources_at, answer_at))
            check("答案含可点击的引用角标",
                  page.locator('.msg.assistant a[href^="#cite-"]').count() > 0)

            # 等生成结束再截图，否则截到半截答案
            for _ in range(60):
                if page.locator(".caret").count() == 0:
                    break
                page.wait_for_timeout(500)
            shot("09-qa")
        else:
            shot("09-qa")

        # ---------------------------------------------------------------- 9
        section("9. 清理")
        page.goto(created_url)
        page.wait_for_timeout(1200)
        delete_btn = page.locator("button:has-text('删除')")
        if delete_btn.count() > 0:
            delete_btn.first.click()
            page.wait_for_timeout(600)
            confirm = page.locator(".n-dialog button:has-text('删除')")
            if confirm.count() > 0:
                confirm.first.click()
                page.wait_for_timeout(1500)
        check("清理完成", True)

        # ---------------------------------------------------------------- 10
        section("10. 控制台健康度")
        # Vue 的组件解析失败会以 warning 形式出现，页面看着正常但功能已坏
        unresolved = [e for e in console_errors if "Failed to resolve component" in e]
        check("无组件解析失败", not unresolved, unresolved[:2])
        vue_warns = [e for e in console_errors if "Vue warn" in e]
        check("无 Vue 警告", not vue_warns, vue_warns[:2])
        check("无未捕获异常", not page_errors, page_errors[:2])
        other = [e for e in console_errors
                 if "Failed to resolve component" not in e and "Vue warn" not in e]
        if other:
            print("      其他控制台输出（供参考）: %s" % other[:3])

        browser.close()

    print("\n" + "=" * 74)
    total = len(PASSED) + len(FAILED)
    print("结果: %d/%d 通过" % (len(PASSED), total))
    if FAILED:
        print("\n失败项:")
        for n in FAILED:
            print("  - " + n)
    print("截图目录: %s" % os.path.normpath(SHOT_DIR))
    print("=" * 74)
    return 1 if FAILED else 0


if __name__ == "__main__":
    sys.exit(main())
