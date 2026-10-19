"""会话与多轮记忆的对抗性验收脚本。

覆盖正常路径之外的越权、归属、匿名与清理：
  1. 普通用户已改名为 user，旧用户名 zhangsan 必须失效
  2. 登录用户能看到自己的会话列表，看不到别人的
  3. 拿别人的 chatId 续聊 / 读历史 / 删会话必须被拒
  4. 同一会话的追问能带上上一轮上下文
  5. 删除会话后历史与列表同时消失
  6. 匿名提问可用，但不产生会话列表

运行：python -I scripts/session_check.py
"""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8090"
ok = 0
bad = 0


def check(name, cond, detail=""):
    global ok, bad
    if cond:
        ok += 1
        print(f"  PASS  {name}")
    else:
        bad += 1
        print(f"  FAIL  {name}  {detail}")


def call(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", token)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, {"raw": raw}


def login(username, password):
    status, result = call("POST", "/api/auth/login",
                          {"username": username, "password": password})
    if status == 200 and result.get("code") == 0:
        return result["data"]["token"]
    return None


def ask(question, token=None, chat_id=None):
    """发一次流式提问，返回 (chatId, 答案文本, 错误消息)。"""
    body = {"question": question}
    if chat_id:
        body["chatId"] = chat_id
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/qa/ask", data=data, method="POST")
    req.add_header("Content-Type", "application/json; charset=utf-8")
    req.add_header("Accept", "text/event-stream")
    if token:
        req.add_header("Authorization", token)

    answer, error, done_id = [], None, None
    with urllib.request.urlopen(req, timeout=180) as resp:
        event = None
        for raw_line in resp:
            line = raw_line.decode("utf-8").rstrip("\n")
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:"):
                payload = line[5:].strip()
                try:
                    parsed = json.loads(payload)
                except json.JSONDecodeError:
                    continue
                if event == "delta":
                    answer.append(parsed.get("text", ""))
                elif event == "done":
                    done_id = parsed.get("chatId")
                elif event == "error":
                    error = parsed.get("message")
    return done_id, "".join(answer), error


print("== 1. 用户名变更 ==")
admin = login("admin", "admin123")
normal = login("user", "user123")
check("admin 登录成功", admin is not None)
check("普通用户 user 登录成功", normal is not None)
check("旧用户名 zhangsan 已失效", login("zhangsan", "user123") is None)

status, result = call("POST", "/api/qa/reindex", token=normal)
check("普通用户不能触发全量重建（1003）", result.get("code") == 1003, f"{status} {result}")

print("== 2. 匿名与会话列表 ==")
status, result = call("GET", "/api/qa/sessions")
check("匿名访问会话列表返回空", status == 200 and result.get("data") == [],
      f"{status} {result}")
anon_id, anon_answer, anon_error = ask("MySQL 索引用了什么数据结构？")
check("匿名可以提问", anon_id is not None and anon_error is None, str(anon_error))
status, result = call("GET", "/api/qa/sessions")
check("匿名提问不产生会话列表", result.get("data") == [], str(result))

print("== 3. 会话创建与标题 ==")
admin_id, answer1, error1 = ask("MySQL 索引用了什么数据结构？", token=admin)
check("管理员提问成功", admin_id and error1 is None, str(error1))
check("首轮答案非空", len(answer1) > 20, answer1[:60])
status, sessions = call("GET", "/api/qa/sessions", token=admin)
titles = [s["title"] for s in sessions.get("data", [])]
check("会话出现在管理员列表", admin_id in [s["chatId"] for s in sessions.get("data", [])],
      str(titles))
check("标题取自第一句提问", any("MySQL 索引" in t for t in titles), str(titles))

status, sessions = call("GET", "/api/qa/sessions", token=normal)
check("普通用户看不到管理员的会话", admin_id not in
      [s["chatId"] for s in sessions.get("data", [])], str(sessions.get("data")))

print("== 4. 越权 ==")
status, result = call("GET", f"/api/qa/history/{admin_id}", token=normal)
check("读他人历史被拒（1003）", result.get("code") == 1003, f"{status} {result}")
status, result = call("DELETE", f"/api/qa/sessions/{admin_id}", token=normal)
check("删他人会话被拒（1003）", result.get("code") == 1003, f"{status} {result}")
_, _, cross_error = ask("接着上面说", token=normal, chat_id=admin_id)
check("拿他人 chatId 续聊被拒", cross_error is not None and "无权" in cross_error,
      str(cross_error))
status, result = call("GET", f"/api/qa/history/{admin_id}")
check("匿名读登录用户历史被拒（1002）", result.get("code") == 1002, f"{status} {result}")

print("== 5. 多轮记忆 ==")
_, answer2, error2 = ask("它有什么缺点？", token=admin, chat_id=admin_id)
check("追问成功", error2 is None, str(error2))
print("  追问答案片段：", answer2[:160].replace("\n", " "))
lowered = answer2.lower()
check("追问答案与上一轮主题连续（提到索引/B+ 树/树）",
      any(k in answer2 for k in ("B+", "B+树", "索引", "树")), answer2[:120])
status, history = call("GET", f"/api/qa/history/{admin_id}", token=admin)
check("同一会话累计 4 条消息", len(history.get("data", [])) == 4,
      str(len(history.get("data", []))))

print("== 6. 删除会话 ==")
status, result = call("DELETE", f"/api/qa/sessions/{admin_id}", token=admin)
check("本人删除会话成功", result.get("code") == 0, f"{status} {result}")
status, sessions = call("GET", "/api/qa/sessions", token=admin)
check("删除后列表不再包含", admin_id not in [s["chatId"] for s in sessions.get("data", [])])
status, history = call("GET", f"/api/qa/history/{admin_id}", token=admin)
check("删除后历史为空", history.get("data") == [], str(history))

print(f"\n结果：{ok} 通过 / {bad} 失败")
raise SystemExit(0 if bad == 0 else 1)
