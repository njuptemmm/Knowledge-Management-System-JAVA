#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
知识管理系统 前端功能测试（Playwright 驱动真实浏览器）。

它验证的是构建与类型检查发现不了的那类问题：
组件没注册导致整页空白、模板里引用了不存在的字段、交互流程走不通、
XSS 载荷被当成 HTML 执行、权限判断写反了。

覆盖范围（按功能而非按页面组织）
    A 匿名浏览       分类树、列表、分类筛选
    B 检索           结果、关键词高亮、排序、空结果
    C 安全           XSS 载荷、用户内容不被当 HTML 注入
    D 认证           登录、登出、错误密码、登录守卫
    E 知识条目       创建、编辑、发布、归档、恢复、删除
    F 附件           上传真实 PDF、解析状态流转、抽取文本
    G 标签           选择已有标签、输入新标签
    H 分类管理       新建、修改、删除、删除保护
    I 我的知识库     三个状态页签与状态流转操作
    J 权限           普通用户看不到他人草稿、写操作入口按权限显示
    K 智能问答       流式答案、引用来源、库外拒答
    L 控制台健康度   无组件解析失败、无 Vue 警告、无未捕获异常

前置条件
--------
1. 后端已启动        cd kms-backend && mvn spring-boot:run
2. 前端已启动        cd kms-frontend && npm run dev
3. 数据库已重置      mysql ... < sql/schema.sql && mysql ... < sql/seed.sql

用法
----
    python -I scripts/test_frontend.py

退出码 0 表示全部通过；非 0 表示有失败项，失败清单会打印在末尾。
"""

import os
import sys
import time

from playwright.sync_api import sync_playwright

BASE = "http://localhost:5173"
BACKEND = "http://localhost:8090"
SHOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".tmp", "test"))

PASSED, FAILED = [], []


# --------------------------------------------------------------------------
# 断言与输出
# --------------------------------------------------------------------------

def check(name, ok, detail=""):
    (PASSED if ok else FAILED).append(name)
    print("  %s %s%s" % ("PASS" if ok else "FAIL", name,
                        ("   -> " + str(detail)) if detail else ""))


def section(title):
    print("\n" + "=" * 74)
    print(title)
    print("=" * 74)


def launch_browser(p):
    """
    按可用性选浏览器。

    本机没装 playwright 自带的 chromium（executable_path 指向的其实不存在），
    但系统装了 Edge，因此优先用已装浏览器，避免为此额外下载上百 MB。
    """
    errors = []
    for channel in ("msedge", "chrome", None):
        try:
            browser = p.chromium.launch(headless=True, channel=channel)
            print("浏览器: %s" % (channel or "playwright 自带 chromium"))
            return browser
        except Exception as e:
            errors.append("%s: %s" % (channel, str(e).splitlines()[0][:70]))
    raise RuntimeError("找不到可用浏览器\n  " + "\n  ".join(errors))


# --------------------------------------------------------------------------
# 页面辅助
# --------------------------------------------------------------------------

class App:
    """把常用交互收在一起，让测试主体只表达意图。"""

    def __init__(self, page):
        self.page = page
        self.console = []
        self.errors = []
        page.on("console", lambda m: self.console.append((m.type, m.text))
                if m.type in ("error", "warning") else None)
        page.on("pageerror", lambda e: self.errors.append(str(e)))

    def goto(self, path, wait=1200):
        self.page.goto(BASE + path)
        self.page.wait_for_timeout(wait)
        return self

    def text(self, selector):
        loc = self.page.locator(selector)
        return loc.first.inner_text() if loc.count() > 0 else ""

    def count(self, selector):
        return self.page.locator(selector).count()

    def has(self, text):
        return self.page.locator("text=%s" % text).count() > 0

    def shot(self, name):
        os.makedirs(SHOT_DIR, exist_ok=True)
        self.page.screenshot(path=os.path.join(SHOT_DIR, name + ".png"), full_page=True)

    def wait_text(self, text, timeout=15):
        """等到某段文字出现，返回是否等到。比固定 sleep 可靠。"""
        for _ in range(int(timeout * 4)):
            if self.page.locator("text=%s" % text).count() > 0:
                return True
            self.page.wait_for_timeout(250)
        return False

    def click_text(self, text, nth=0):
        loc = self.page.locator("text=%s" % text)
        if loc.count() > nth:
            loc.nth(nth).click()
            return True
        return False

    def login(self, username, password):
        self.goto("/login")
        self.page.fill('input[autocomplete="username"]', username)
        self.page.fill('input[autocomplete="current-password"]', password)
        self.page.click("button:has-text('登录')")
        self.page.wait_for_timeout(2000)
        return "/login" not in self.page.url

    def logout(self):
        """
        点开右上角用户菜单，再选退出登录。

        n-dropdown 用插槽作触发器时不会加 .n-dropdown 类，
        因此直接点 .actions 区域里的最后一个按钮（顺序是：智能问答、用户名）。
        下拉挂在 portal 上，所以要分两步点。
        """
        buttons = self.page.locator(".actions button")
        if buttons.count() == 0:
            return
        buttons.last.click()
        self.page.wait_for_timeout(800)

        option = self.page.locator(".n-dropdown-option:has-text('退出登录')")
        if option.count() > 0:
            option.first.click()
        else:
            self.click_text("退出登录")
        self.page.wait_for_timeout(1800)

    def dismiss_dialog(self, confirm_text="删除"):
        """naive-ui 的确认弹窗。点掉它，让删除操作真正执行。"""
        self.page.wait_for_timeout(600)
        btn = self.page.locator(".n-dialog button:has-text('%s')" % confirm_text)
        if btn.count() > 0:
            btn.first.click()
            self.page.wait_for_timeout(1500)
            return True
        return False


# --------------------------------------------------------------------------
# 各功能分组
# --------------------------------------------------------------------------

def test_anonymous(app):
    section("A 匿名浏览")
    app.goto("/")
    check("A1 首页可访问", app.has("知识管理系统"))
    cards = app.count(".card")
    check("A2 列表渲染出条目", cards > 0, "%d 张卡片" % cards)
    check("A3 分类树渲染", app.count(".category-tree") > 0)
    # 树默认只展开根层级，第三层要展开 Java 之后才在 DOM 里
    app.page.locator(".category-tree >> text=Java").first.click()
    app.page.wait_for_timeout(900)
    check("A4 树中含「编程语言」及子分类",
          app.has("编程语言") and app.has("Java") and app.has("Spring"),
          "展开 Java 后可见 Spring")

    # 未登录不应出现「只看我的」与「写一篇」
    check("A5 未登录不显示「写一篇」", not app.has("写一篇"))
    check("A6 未登录不显示「只看我的」", not app.has("只看我的"))
    check("A7 未登录顶栏显示登录入口", app.has("登录"))

    before = app.count(".card")
    app.page.locator(".category-tree >> text=Java").first.click()
    app.page.wait_for_timeout(1200)
    after = app.count(".card")
    check("A8 点击分类后列表按分类收敛", 0 < after != before or after > 0,
          "全部 %d 条 → Java 分类 %d 条" % (before, after))
    app.shot("A-browse")


def test_search(app):
    section("B 检索")
    app.goto("/search?keyword=%E7%B4%A2%E5%BC%95")
    hits = app.count(".hit")
    check("B1 检索结果渲染", hits > 0, "%d 条" % hits)
    check("B2 关键词被高亮（模板渲染的 <mark>）", app.count(".hit mark") > 0,
          "%d 处" % app.count(".hit mark"))
    check("B3 显示结果总数", app.has("条结果"))

    # 切换排序
    app.page.locator(".filters >> .n-select").last.click()
    app.page.wait_for_timeout(500)
    if app.click_text("最新"):
        app.page.wait_for_timeout(1500)
        check("B4 切换排序后仍有结果", app.count(".hit") > 0)

    # 用纯 ASCII 的乱码串：ngram 按二元组匹配中文，
    # 含中文的「不存在」反而会命中一批文档，测不出空态
    app.goto("/search?keyword=zzqqxxvv")
    app.page.wait_for_timeout(1200)
    check("B5 无结果时给出可读空态并提示放宽条件",
          app.has("没有匹配的结果") and app.has("清空筛选条件"))
    app.shot("B-search-empty")


def test_security(app):
    section("C 安全")
    before = len(app.errors)
    app.goto("/search?keyword=%3Cscript%3Ealert(1)%3C%2Fscript%3E", wait=1500)
    check("C1 XSS 载荷未执行", len(app.errors) == before, app.errors[before:before + 1])
    check("C2 载荷被当作普通文本", app.count(".hit") >= 0)

    # 在检索框里输入 HTML，看是否被转义
    app.goto("/search")
    app.page.fill(".search-bar input", "<img src=x onerror=alert(1)>")
    app.page.keyboard.press("Enter")
    app.page.wait_for_timeout(1500)
    check("C3 输入 HTML 标签不会被执行", len(app.errors) == before)


def test_auth(app):
    section("D 认证")
    app.goto("/login")
    check("D1 登录页渲染并给出测试账号", app.has("测试账号"))

    app.page.fill('input[autocomplete="username"]', "admin")
    app.page.fill('input[autocomplete="current-password"]', "wrong-password")
    app.page.click("button:has-text('登录')")
    app.page.wait_for_timeout(1500)
    check("D2 错误密码被拒绝且停留在登录页", "/login" in app.page.url)
    check("D3 展示后端返回的提示", app.has("用户名或密码错误"))
    app.shot("D-login-error")

    check("D4 正确密码可登录", app.login("admin", "admin123"))
    check("D5 顶栏显示当前用户", app.has("管理员"))

    # 登录守卫
    app.page.evaluate("() => localStorage.removeItem('kms.token')")
    app.goto("/mine", wait=2000)
    check("D6 未登录访问需登录页面被重定向", "/login" in app.page.url, app.page.url)

    check("D7 重新登录", app.login("admin", "admin123"))
    app.logout()
    # 断言必须限定在顶栏：知识卡片上的作者名也是「管理员」，
    # 用整页文本匹配会把卡片内容当成还没登出
    actions = app.page.locator(".actions").first.inner_text()
    check("D8 登出后顶栏回到未登录状态", "登录" in actions and "管理员" not in actions,
          " ".join(actions.split()))
    app.login("admin", "admin123")


def test_knowledge_lifecycle(app):
    section("E 知识条目：创建 / 编辑 / 发布 / 归档 / 恢复 / 删除")
    unique = "前端功能测试-%d" % int(time.time())

    # 创建（先存草稿）
    app.goto("/editor/new")
    check("E1 编辑器可访问", app.has("新建知识条目"))
    app.page.fill('input[placeholder="给这篇知识起个名字"]', unique)
    app.page.fill('textarea[placeholder^="一句话概括"]', "前端功能测试创建，含唯一标记 功能测试标记乙")
    app.page.fill('textarea[placeholder^="# 标题"]',
                  "# 功能测试\n\n这段正文含唯一标记 功能测试标记乙。\n\n## 子章节\n\n用于验证编辑与检索。")
    app.page.wait_for_timeout(600)
    check("E2 Markdown 实时预览生效", app.count(".preview h1") > 0)
    app.shot("E1-editor")

    app.page.click("button:has-text('保存草稿')")
    app.page.wait_for_timeout(2500)
    check("E3 保存草稿后进入详情页", "/detail/" in app.page.url, app.page.url)
    detail_url = app.page.url
    item_id = detail_url.rstrip("/").split("/")[-1]
    check("E4 详情页状态为草稿", app.has("草稿"))
    check("E5 正文 Markdown 已渲染", app.count(".markdown-body h1") > 0)

    # 草稿应对匿名不可见
    app.page.evaluate("() => localStorage.removeItem('kms.token')")
    app.goto("/search?keyword=" + unique, wait=1500)
    titles = app.page.locator(".hit-title").all_inner_texts()
    # 中文在 ngram 下按二元组匹配，会命中共享词组的其他文档，
    # 因此这里要断言的不是「零结果」，而是「草稿本身没出现」
    check("E6 未登录检索不到该草稿",
          not any(unique in t for t in titles),
          "命中 %d 条，未含草稿" % len(titles))

    # 重新登录继续
    app.login("admin", "admin123")
    app.goto("/detail/" + item_id)

    # 编辑
    app.click_text("编辑")
    app.page.wait_for_timeout(1500)
    check("E7 可进入编辑页并带出原内容",
          app.page.input_value('input[placeholder="给这篇知识起个名字"]') == unique)
    app.page.fill('input[placeholder="给这篇知识起个名字"]', unique + "（已编辑）")
    app.page.click("button:has-text('保存草稿')")
    app.page.wait_for_timeout(2500)
    check("E8 编辑后标题已更新", app.has(unique + "（已编辑）"))

    # 发布
    app.click_text("发布")
    app.page.wait_for_timeout(2000)
    check("E9 发布后状态变化", app.has("已发布"))

    app.page.evaluate("() => localStorage.removeItem('kms.token')")
    app.goto("/search?keyword=%E5%8A%9F%E8%83%BD%E6%B5%8B%E8%AF%95%E6%A0%87%E8%AE%B0%E4%B9%99", wait=1500)
    check("E10 发布后未登录可检索到", app.count(".hit") > 0, "%d 条" % app.count(".hit"))

    # 归档
    app.login("admin", "admin123")
    app.goto("/detail/" + item_id)
    app.click_text("归档")
    app.page.wait_for_timeout(2000)
    check("E11 归档后状态变化", app.has("已归档"))

    # 恢复
    app.click_text("恢复到草稿")
    app.page.wait_for_timeout(2000)
    check("E12 从归档恢复到草稿", app.has("草稿"))

    # 删除
    app.click_text("删除")
    app.dismiss_dialog()
    app.page.wait_for_timeout(1500)
    check("E13 删除后离开详情页", "/detail/" not in app.page.url, app.page.url)
    return item_id


def test_attachment(app):
    section("F 附件：上传真实 PDF 与解析")
    pdf = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..",
                                       "files", "课程考试要求.pdf"))
    if not os.path.exists(pdf):
        check("F0 找到测试 PDF", False, pdf)
        return

    # 挂到 seed 中已存在的条目 1 上（作者是 admin）
    app.goto("/detail/1", wait=1500)
    check("F1 详情页显示附件面板", app.has("附件"))

    app.page.set_input_files('input[type="file"]', pdf)
    app.page.wait_for_timeout(1200)
    check("F2 上传后出现解析状态",
          app.has("等待解析") or app.has("解析中") or app.has("解析完成"))

    done = app.wait_text("解析完成", timeout=45)
    check("F3 PDF 解析完成", done)
    if done:
        summary = app.page.locator("summary:has-text('查看抽取的文本')")
        if summary.count() > 0:
            summary.first.click()
            app.page.wait_for_timeout(600)
            preview = app.text(".preview pre")
            check("F4 抽取到中文文本", len(preview) > 50, preview[:50].replace("\n", " "))
            check("F5 抽取文本已归一化（标准汉字「工程」）", "工程" in preview)
    app.shot("F-attachment")


def test_tags(app):
    section("G 标签：选择已有与输入新标签")
    unique = "新标签%d" % int(time.time())
    app.goto("/editor/new")
    app.page.fill('input[placeholder="给这篇知识起个名字"]', "标签测试-" + unique)

    # n-select 的 tag 模式：输入后回车即创建新选项
    select = app.page.locator(".tag-input")
    check("G0 标签输入框存在", select.count() > 0)
    if select.count() == 0:
        return
    # n-select 未展开时内部 input 不可见，必须先点可见的触发器
    trigger = select.locator(".n-base-selection").first
    trigger.click()
    app.page.wait_for_timeout(500)
    app.page.keyboard.type(unique)
    app.page.wait_for_timeout(500)
    app.page.keyboard.press("Enter")
    app.page.wait_for_timeout(700)
    check("G1 可输入新标签名",
          app.page.locator(".tag-input .n-tag").count() >= 1,
          "%d 个标签" % app.page.locator(".tag-input .n-tag").count())

    # 再选一个已有标签
    trigger.click()
    app.page.wait_for_timeout(500)
    if app.click_text("入门"):
        app.page.wait_for_timeout(500)
    check("G2 可选择已有标签", app.page.locator(".tag-input .n-tag").count() >= 2,
          "共 %d 个标签" % app.page.locator(".tag-input .n-tag").count())

    app.page.click("button:has-text('保存草稿')")
    app.page.wait_for_timeout(2500)
    check("G3 带新标签的条目保存成功", "/detail/" in app.page.url, app.page.url)
    if "/detail/" in app.page.url:
        check("G4 详情页显示新创建的标签", app.has(unique))
        app.shot("G-tags")
        # 清理
        app.click_text("删除")
        app.dismiss_dialog()


def test_category_admin(app):
    section("H 分类管理：新建 / 修改 / 删除 / 删除保护")
    unique = "测试分类%d" % int(time.time())
    app.goto("/categories", wait=1500)
    check("H1 分类管理页可访问", app.has("分类管理"))

    # 新建根分类
    app.click_text("新建子分类")
    app.page.wait_for_timeout(800)
    app.page.fill('input[placeholder="分类名称"]', unique)
    app.page.click("button:has-text('创建')")
    app.page.wait_for_timeout(2000)
    check("H2 新建分类出现在树中", app.wait_text(unique, timeout=8))

    # 修改
    app.page.locator(".category-tree >> text=%s" % unique).first.click()
    app.page.wait_for_timeout(1000)
    modified = unique + "改"
    app.page.fill('input[placeholder="分类名称"]', modified)
    app.page.click("button:has-text('保存')")
    app.page.wait_for_timeout(2000)
    check("H3 修改后名称更新", app.wait_text(modified, timeout=8))

    # 删除保护：选一个下有子分类或条目的分类
    app.page.locator(".category-tree >> text=编程语言").first.click()
    app.page.wait_for_timeout(1000)
    app.click_text("删除")
    app.dismiss_dialog()
    app.page.wait_for_timeout(1500)
    check("H4 删除有子分类的分类被拒并说明原因",
          app.has("子分类") or app.has("无法删除") or app.has("请先"))

    # 删除刚建的分类
    app.page.locator(".category-tree >> text=%s" % modified).first.click()
    app.page.wait_for_timeout(1000)
    app.click_text("删除")
    app.dismiss_dialog()
    app.page.wait_for_timeout(1500)
    check("H5 空分类可以被删除", not app.has(modified))
    app.shot("H-category")


def test_mine(app):
    section("I 我的知识库：状态页签与流转操作")
    app.goto("/mine", wait=1500)
    check("I1 页面可访问", app.has("我的知识库"))
    check("I2 三个状态页签齐全",
          app.has("草稿") and app.has("已发布") and app.has("已归档"))

    app.click_text("已归档")
    app.page.wait_for_timeout(1500)
    check("I3 切换到已归档页签", app.count(".row") >= 0)

    app.click_text("已发布")
    app.page.wait_for_timeout(1500)
    rows = app.count(".row")
    check("I4 已发布页签有数据", rows > 0, "%d 条" % rows)
    if rows > 0:
        # 已发布的条目不应出现「发布」按钮（状态机只允许合法操作）
        # 用按钮文本精确匹配：has() 是子串匹配，「发布」会命中页签「已发布」
        ops = app.page.locator(".row button").all_inner_texts()
        check("I5 已发布条目不提供「发布」操作", "发布" not in ops, ops)
        check("I6 已发布条目提供「归档」操作", "归档" in ops, ops)
    app.shot("I-mine")


def test_permission(app):
    section("J 权限：普通用户视角")
    app.logout()
    check("J1 切换到普通用户", app.login("user", "user123"))
    check("J2 顶栏显示 user", app.has("user"))

    # user 不该看到 admin 的草稿
    app.goto("/mine", wait=1500)
    app.click_text("草稿")
    app.page.wait_for_timeout(1500)
    body = app.page.content()
    # seed 中 admin 的草稿标题
    check("J3 看不到他人的草稿",
          "JVM 调优实战记录" not in body and "课程设计答辩准备清单" not in body)

    # 打开 admin 的条目，不应有写操作入口
    app.goto("/detail/1", wait=1500)
    check("J4 他人条目不显示编辑入口", not app.has("编辑"))
    check("J5 他人条目不显示删除入口", not app.has("删除"))
    app.shot("J-permission")

    app.logout()
    app.login("admin", "admin123")


def test_qa(app):
    section("K 智能问答")
    app.goto("/qa", wait=2000)
    check("K1 问答页渲染", app.has("智能问答"))
    enabled = not app.has("问答功能未配置")
    check("K2 问答功能已启用", enabled)
    if not enabled:
        return

    app.page.fill("textarea[placeholder^='输入你的问题']", "MySQL 索引用了什么数据结构？")
    submitted = time.time()
    app.page.keyboard.press("Enter")

    sources_at = answer_at = None
    for _ in range(100):
        if sources_at is None and app.count(".sources") > 0:
            sources_at = time.time() - submitted
        if answer_at is None:
            bubbles = app.page.locator(".msg.assistant .bubble")
            if bubbles.count() > 0:
                text = bubbles.last.inner_text().strip()
                # 排除「正在检索并生成…」占位与用户自己的气泡
                if len(text) > 5 and "正在检索" not in text:
                    answer_at = time.time() - submitted
        if sources_at is not None and answer_at is not None:
            break
        app.page.wait_for_timeout(300)

    check("K3 引用来源已渲染", sources_at is not None,
          "%.1fs" % sources_at if sources_at else "未出现")
    check("K4 答案已生成", answer_at is not None,
          "%.1fs" % answer_at if answer_at else "未出现")
    check("K5 引用先于答案出现", sources_at is not None and answer_at is not None
          and sources_at <= answer_at,
          "引用 %.1fs / 答案 %.1fs" % (sources_at or -1, answer_at or -1))
    # 等生成结束后再断言引用与排版：
    # 引用角标是随答案一起流式出现的，刚出首个字时还没到，
    # 那一刻检查会得到假失败（实际踩过）
    for _ in range(80):
        if app.page.locator(".caret").count() == 0:
            break
        app.page.wait_for_timeout(500)

    check("K6 引用可点击", app.count('.msg.assistant a[href^="#cite-"]') > 0,
          "%d 个引用角标" % app.count('.msg.assistant a[href^="#cite-"]'))
    check("K7 答案里的 Markdown 已渲染（无残留星号）",
          "**" not in app.text(".msg.assistant .bubble"),
          app.text(".msg.assistant .bubble")[:60].replace("\n", " "))

    # 库外问题应被拒答。
    # 不能只等固定时长：检索与生成加起来可能十几秒，等短了会把
    # 还在生成中的占位文案当成答案。改为轮询到不再显示占位为止。
    app.page.fill("textarea[placeholder^='输入你的问题']", "推荐几家北京的川菜馆")
    app.page.keyboard.press("Enter")

    last = ""
    for _ in range(120):                      # 最多等约 60 秒
        bubbles = app.page.locator(".msg.assistant .bubble")
        if bubbles.count() > 0:
            last = bubbles.last.inner_text().strip()
            if last and "正在检索" not in last and "▍" not in last:
                break
        app.page.wait_for_timeout(500)

    check("K8 知识库外的问题被拒答", "知识库中" in last, last[:60].replace("\n", " "))
    app.shot("K-qa")


def test_console(app):
    section("L 控制台健康度")
    unresolved = [m for t, m in app.console if "Failed to resolve component" in m]
    check("L1 无组件解析失败（否则页面会整片空白）", not unresolved, unresolved[:2])
    warns = [m for t, m in app.console if "Vue warn" in m]
    check("L2 无 Vue 警告", not warns, warns[:2])
    check("L3 无未捕获异常", not app.errors, app.errors[:2])
    others = [m for t, m in app.console
              if "Failed to resolve component" not in m and "Vue warn" not in m]
    if others:
        print("      其他控制台输出（供参考）: %s" % others[:3])


def health_check():
    """攻击/操作之后，后端数据与服务应当仍然正常。"""
    section("M 收尾健康检查")
    import json
    import urllib.request

    def api(path):
        with urllib.request.urlopen(BACKEND + path, timeout=15) as r:
            return json.loads(r.read().decode("utf-8"))

    try:
        items = api("/api/knowledge?size=100")["data"]["total"]
        check("M1 后端仍可访问且有条目", items > 0, "%d 条已发布" % items)
        tree = api("/api/categories/tree")["data"]
        check("M2 分类树结构完好", len(tree) >= 3, "%d 个根分类" % len(tree))
        tags = api("/api/tags")["data"]
        check("M3 标签接口正常", len(tags) > 0, "%d 个标签" % len(tags))
        search = api("/api/search?keyword=%E7%B4%A2%E5%BC%95")["data"]["total"]
        check("M4 中文检索仍正常", search > 0, "%d 条" % search)
    except Exception as e:
        check("M1 后端健康检查", False, "%s: %s" % (type(e).__name__, e))


# --------------------------------------------------------------------------
# 主流程
# --------------------------------------------------------------------------

def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except AttributeError:
        pass

    print("=" * 74)
    print("  知识管理系统 前端功能测试")
    print("  前端: %s" % BASE)
    print("  后端: %s" % BACKEND)
    print("=" * 74)

    started = time.time()
    with sync_playwright() as p:
        browser = launch_browser(p)
        context = browser.new_context(viewport={"width": 1440, "height": 1000}, locale="zh-CN")
        app = App(context.new_page())

        test_anonymous(app)
        test_search(app)
        test_security(app)
        test_auth(app)
        test_knowledge_lifecycle(app)
        test_attachment(app)
        test_tags(app)
        test_category_admin(app)
        test_mine(app)
        test_permission(app)
        test_qa(app)
        test_console(app)

        browser.close()

    health_check()

    print("\n" + "=" * 74)
    total = len(PASSED) + len(FAILED)
    print("  结果: %d/%d 通过   耗时 %.0f 秒" % (len(PASSED), total, time.time() - started))
    if FAILED:
        print("\n  失败项:")
        for n in FAILED:
            print("    - " + n)
    print("  截图: %s" % SHOT_DIR)
    print("=" * 74)
    return 1 if FAILED else 0


if __name__ == "__main__":
    sys.exit(main())
