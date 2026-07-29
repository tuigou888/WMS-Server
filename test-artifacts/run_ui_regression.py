#!/usr/bin/env python3
"""Headless Chrome UI regression for the WMS React application.

Uses Chrome DevTools Protocol via a small standard-library WebSocket client so
it does not require Selenium/Playwright or third-party Python packages.
"""
from __future__ import annotations

import base64
import hashlib
import json
import os
import shutil
import socket
import struct
import subprocess
import sys
import tempfile
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse

BASE_URL = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:5173/"
OUT_PATH = Path(__file__).with_name("ui-results-current.json")
SHOT_PATH = Path(__file__).with_name("ui-admin-dashboard-current.png")


class CDP:
    def __init__(self, ws_url: str):
        u = urlparse(ws_url)
        self.sock = socket.create_connection((u.hostname, u.port), timeout=10)
        key = base64.b64encode(os.urandom(16)).decode()
        request = (
            f"GET {u.path} HTTP/1.1\r\n"
            f"Host: {u.hostname}:{u.port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            f"Origin: http://{u.hostname}:{u.port}\r\n\r\n"
        )
        self.sock.sendall(request.encode())
        response = self._read_http_headers()
        if b" 101 " not in response.split(b"\r\n", 1)[0]:
            raise RuntimeError(f"WebSocket handshake failed: {response[:500]!r}")
        accept = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest())
        if accept.lower() not in response.lower():
            raise RuntimeError("WebSocket handshake returned an invalid accept key")
        self.next_id = 1
        self.events: list[dict] = []
        self._fragment = bytearray()
        self._fragment_opcode = None

    def _read_http_headers(self) -> bytes:
        data = bytearray()
        while b"\r\n\r\n" not in data:
            chunk = self.sock.recv(4096)
            if not chunk:
                break
            data.extend(chunk)
        return bytes(data)

    def _read_exact(self, n: int) -> bytes:
        data = bytearray()
        while len(data) < n:
            chunk = self.sock.recv(n - len(data))
            if not chunk:
                raise EOFError("WebSocket closed")
            data.extend(chunk)
        return bytes(data)

    def _send_frame(self, payload: bytes, opcode: int = 1):
        first = 0x80 | opcode
        n = len(payload)
        if n < 126:
            header = bytes([first, 0x80 | n])
        elif n < 65536:
            header = bytes([first, 0x80 | 126]) + struct.pack("!H", n)
        else:
            header = bytes([first, 0x80 | 127]) + struct.pack("!Q", n)
        mask = os.urandom(4)
        masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
        self.sock.sendall(header + mask + masked)

    def _recv_message(self) -> str | None:
        while True:
            first, second = self._read_exact(2)
            fin = bool(first & 0x80)
            opcode = first & 0x0F
            masked = bool(second & 0x80)
            n = second & 0x7F
            if n == 126:
                n = struct.unpack("!H", self._read_exact(2))[0]
            elif n == 127:
                n = struct.unpack("!Q", self._read_exact(8))[0]
            mask = self._read_exact(4) if masked else b""
            payload = self._read_exact(n)
            if masked:
                payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
            if opcode == 8:
                return None
            if opcode == 9:
                self._send_frame(payload, opcode=10)
                continue
            if opcode in (1, 2):
                self._fragment = bytearray(payload)
                self._fragment_opcode = opcode
            elif opcode == 0:
                self._fragment.extend(payload)
            else:
                continue
            if fin:
                data = bytes(self._fragment)
                self._fragment.clear()
                self._fragment_opcode = None
                return data.decode("utf-8")

    def command(self, method: str, params: dict | None = None, timeout: float = 30):
        command_id = self.next_id
        self.next_id += 1
        self._send_frame(json.dumps({"id": command_id, "method": method, "params": params or {}}, ensure_ascii=False).encode())
        deadline = time.time() + timeout
        while time.time() < deadline:
            self.sock.settimeout(max(0.1, deadline - time.time()))
            raw = self._recv_message()
            if raw is None:
                raise RuntimeError("Chrome DevTools WebSocket closed")
            msg = json.loads(raw)
            if msg.get("id") == command_id:
                if "error" in msg:
                    raise RuntimeError(f"CDP {method} failed: {msg['error']}")
                return msg.get("result", {})
            if "method" in msg:
                self.events.append(msg)
        raise TimeoutError(f"Timed out waiting for CDP command {method}")

    def eval(self, expression: str, timeout: float = 30):
        result = self.command("Runtime.evaluate", {
            "expression": expression,
            "awaitPromise": True,
            "returnByValue": True,
            "userGesture": True,
        }, timeout=timeout)
        if "exceptionDetails" in result:
            details = result["exceptionDetails"]
            raise RuntimeError(details.get("exception", {}).get("description") or details.get("text") or "JavaScript exception")
        remote = result.get("result", {})
        if remote.get("subtype") == "error":
            raise RuntimeError(remote.get("description", "JavaScript error"))
        return remote.get("value")

    def screenshot(self, path: Path):
        result = self.command("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": False})
        path.write_bytes(base64.b64decode(result["data"]))

    def close(self):
        try:
            self._send_frame(b"", opcode=8)
        except Exception:
            pass
        self.sock.close()


def wait_http(url: str, timeout=30):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=2) as r:
                return r.read()
        except Exception as e:
            last = e
            time.sleep(0.25)
    raise RuntimeError(f"Timed out waiting for {url}: {last}")


def find_free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def js_string(value):
    return json.dumps(value, ensure_ascii=False)


results: list[dict] = []

def record(name, passed, detail=""):
    results.append({"name": name, "passed": bool(passed), "detail": str(detail)})
    print(("PASS" if passed else "FAIL") + f"  {name}" + (f" — {detail}" if detail else ""))


def assertion(name, actual, expected=True):
    passed = actual == expected
    record(name, passed, f"actual={actual!r}, expected={expected!r}" if not passed else actual)
    return passed


WAIT_HELPERS = r"""
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const waitFor = async (fn, timeout=10000, label='condition') => {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    const value = fn();
    if (value) return value;
    await sleep(100);
  }
  throw new Error('Timed out waiting for ' + label);
};
const text = el => (el?.textContent || '').trim();
"""

LOGIN_JS = lambda username, password: f"""
(async () => {{
{WAIT_HELPERS}
  localStorage.clear();
  await waitFor(() => [...document.querySelectorAll('button')].find(x => text(x) === '登录系统'), 10000, 'login page');
  const inputs = [...document.querySelectorAll('input')];
  const user = inputs.find(x => x.type !== 'password');
  const pass = inputs.find(x => x.type === 'password');
  const setValue = (el, value) => {{
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(el, value);
    el.dispatchEvent(new Event('input', {{bubbles:true}}));
    el.dispatchEvent(new Event('change', {{bubbles:true}}));
  }};
  setValue(user, {js_string(username)});
  setValue(pass, {js_string(password)});
  [...document.querySelectorAll('button')].find(x => text(x) === '登录系统').click();
  await waitFor(() => document.querySelector('.app-shell'), 15000, 'workspace after login');
  await sleep(800);
  return {{
    header: text(document.querySelector('.app-header')),
    menus: [...document.querySelectorAll('.ant-menu-title-content')].map(text),
    pageTitle: text(document.querySelector('.page-title')),
    token: !!localStorage.getItem('wms_token')
  }};
}})()
"""


def navigation_js(menu_label, expected_title):
    return f"""
(async () => {{
{WAIT_HELPERS}
  const menu = await waitFor(() => [...document.querySelectorAll('.ant-menu-title-content')].find(x => text(x) === {js_string(menu_label)}), 5000, 'menu {menu_label}');
  menu.click();
  await waitFor(() => text(document.querySelector('.page-title')) === {js_string(expected_title)}, 10000, 'page {expected_title}');
  await sleep(900);
  return {{title:text(document.querySelector('.page-title')), body:text(document.body).slice(0, 1000)}};
}})()
"""


def main():
    started = datetime.now(timezone.utc).astimezone().isoformat()
    chrome = None
    cdp = None
    profile = Path(tempfile.mkdtemp(prefix="wms-ui-chrome-", dir="/tmp"))
    port = find_free_port()
    try:
        wait_http(BASE_URL, 20)
        chrome = subprocess.Popen([
            "google-chrome", "--headless=new", "--no-sandbox", "--disable-gpu",
            "--disable-dev-shm-usage", "--hide-scrollbars", "--window-size=1600,1000",
            f"--remote-debugging-port={port}", "--remote-debugging-address=127.0.0.1",
            "--remote-allow-origins=*", f"--user-data-dir={profile}", "about:blank",
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        wait_http(f"http://127.0.0.1:{port}/json/version", 20)
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/json/list") as r:
            targets = json.load(r)
        page = next(x for x in targets if x.get("type") == "page")
        cdp = CDP(page["webSocketDebuggerUrl"])
        for domain in ("Page.enable", "Runtime.enable", "Network.enable", "Log.enable"):
            cdp.command(domain)
        cdp.command("Page.navigate", {"url": BASE_URL})
        cdp.eval(f"(async()=>{{{WAIT_HELPERS} await waitFor(()=>document.querySelector('.login-card'),10000,'login card'); return true}})()")
        login_view = cdp.eval("({title:document.title,heading:document.querySelector('h2')?.textContent,button:[...document.querySelectorAll('button')].some(x=>x.textContent.includes('登录系统')),hint:document.body.innerText.includes('operator / operator123')})")
        assertion("登录页标题", login_view.get("heading"), "WMS 管理系统")
        assertion("登录按钮可见", login_view.get("button"), True)
        assertion("演示账号提示可见", login_view.get("hint"), True)

        admin = cdp.eval(LOGIN_JS("admin", "admin123"), timeout=30)
        assertion("管理员登录成功", admin.get("token"), True)
        assertion("管理员身份显示", "管理员" in admin.get("header", ""), True)
        expected_admin_menus = ["仪表盘", "物品档案", "供应商 / 客户", "扫码入库", "扫码出库", "入库 / 出库单", "库存调拨", "库存盘点", "库存管理", "报表中心", "二维码与 Excel", "用户与权限"]
        assertion("管理员菜单完整", admin.get("menus"), expected_admin_menus)
        assertion("默认打开仪表盘", admin.get("pageTitle"), "仪表盘")
        cdp.screenshot(SHOT_PATH)
        record("管理员仪表盘截图", SHOT_PATH.exists() and SHOT_PATH.stat().st_size > 1000, f"{SHOT_PATH} ({SHOT_PATH.stat().st_size} bytes)")

        pages = [
            ("物品档案", "物品档案"), ("供应商 / 客户", "供应商 / 客户"),
            ("扫码入库", "扫码入库"), ("扫码出库", "扫码出库"),
            ("入库 / 出库单", "入库 / 出库单"), ("库存调拨", "库存调拨"),
            ("库存盘点", "库存盘点"), ("库存管理", "库存管理"),
            ("报表中心", "报表中心"), ("二维码与 Excel", "二维码与 Excel"),
            ("用户与权限", "用户与权限"), ("仪表盘", "仪表盘"),
        ]
        for menu, title in pages:
            try:
                value = cdp.eval(navigation_js(menu, title), timeout=25)
                assertion(f"页面切换：{menu}", value.get("title"), title)
            except Exception as e:
                record(f"页面切换：{menu}", False, e)

        # Verify the newly fixed transfer batch field is present in the actual UI.
        try:
            cdp.eval(navigation_js("库存调拨", "库存调拨"), timeout=20)
            batch = cdp.eval(f"""
(async()=>{{
{WAIT_HELPERS}
 const setValue=(el,value)=>{{const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(el,value);el.dispatchEvent(new Event('input',{{bubbles:true}}));el.dispatchEvent(new Event('change',{{bubbles:true}}));}};
 const warehouseBtn=[...document.querySelectorAll('button')].find(x=>text(x).includes('新增仓库'));
 if(!warehouseBtn) throw new Error('new warehouse button not found');
 warehouseBtn.click();
 await waitFor(()=>[...document.querySelectorAll('.ant-modal')].some(x=>text(x).includes('新增仓库') && getComputedStyle(x).display!=='none'),5000,'new warehouse modal');
 const modal=[...document.querySelectorAll('.ant-modal')].find(x=>text(x).includes('新增仓库') && getComputedStyle(x).display!=='none');
 const inputs=[...modal.querySelectorAll('input')];
 setValue(inputs[0],'UI-WH-' + Date.now());
 setValue(inputs[1],'UI 测试仓');
 const ok=modal.querySelector('.ant-btn-primary');
 if(!ok) throw new Error('new warehouse confirm button not found');
 ok.click();
 await waitFor(()=>{{const b=[...document.querySelectorAll('button')].find(x=>text(x).includes('新建调拨'));return b && !b.disabled;}},10000,'second warehouse available');
 const transferBtn=[...document.querySelectorAll('button')].find(x=>text(x).includes('新建调拨'));
 transferBtn.click();
 await waitFor(()=>[...document.querySelectorAll('input')].some(x=>x.placeholder==='批次号（可选）'),5000,'batch field');
 const batchInput=[...document.querySelectorAll('input')].find(x=>x.placeholder==='批次号（可选）');
 return {{visible:!!batchInput && getComputedStyle(batchInput).display!=='none',placeholder:batchInput?.placeholder||''}};
}})()
""", timeout=20)
            assertion("调拨批次字段可见", batch.get("visible") and batch.get("placeholder")=="批次号（可选）", True)
            cdp.eval("[...document.querySelectorAll('.ant-modal-close')].at(-1)?.click(); true")
        except Exception as e:
            record("调拨批次字段可见", False, e)

        # Logout and perform a real operator login.
        logout = cdp.eval(f"""
(async()=>{{
{WAIT_HELPERS}
 const btn=[...document.querySelectorAll('button')].find(x=>text(x)==='退出');
 if(!btn) throw new Error('logout button not found');
 btn.click();
 await waitFor(()=>document.querySelector('.login-card'),10000,'login after logout');
 return !localStorage.getItem('wms_token');
}})()
""", timeout=20)
        assertion("管理员退出成功", logout, True)
        operator = cdp.eval(LOGIN_JS("operator", "operator123"), timeout=30)
        assertion("操作员登录成功", operator.get("token"), True)
        assertion("操作员身份显示", "仓库操作员" in operator.get("header", ""), True)
        assertion("操作员隐藏用户权限菜单", "用户与权限" not in operator.get("menus", []), True)
        assertion("操作员业务菜单数量", len(operator.get("menus", [])), 11)

        # Drain events and classify browser/runtime/network failures.
        cdp.eval("new Promise(r=>setTimeout(()=>r(true),1200))", timeout=5)
        exceptions = []
        console_errors = []
        network_errors = []
        for event in cdp.events:
            method = event.get("method")
            params = event.get("params", {})
            if method == "Runtime.exceptionThrown":
                d = params.get("exceptionDetails", {})
                exceptions.append(d.get("exception", {}).get("description") or d.get("text", "JavaScript exception"))
            elif method == "Log.entryAdded" and params.get("entry", {}).get("level") == "error":
                console_errors.append((params["entry"].get("text", "console error") + " " + params["entry"].get("url", "")).strip())
            elif method == "Network.responseReceived":
                response = params.get("response", {})
                status = int(response.get("status", 0))
                url = response.get("url", "")
                if status >= 500:
                    network_errors.append(f"{status} {url}")
        record("无未捕获 JavaScript 异常", not exceptions, "; ".join(exceptions[:5]))
        # Chrome logs some benign resource messages as errors; preserve details if any.
        record("无浏览器错误日志", not console_errors, "; ".join(console_errors[:5]))
        record("无 HTTP 5xx 响应", not network_errors, "; ".join(network_errors[:5]))

    except Exception as e:
        record("UI 回归脚本执行", False, repr(e))
    finally:
        if cdp:
            cdp.close()
        if chrome:
            chrome.terminate()
            try:
                chrome.wait(timeout=5)
            except subprocess.TimeoutExpired:
                chrome.kill()
        shutil.rmtree(profile, ignore_errors=True)
        summary = {
            "total": len(results),
            "passed": sum(1 for x in results if x["passed"]),
            "failed": sum(1 for x in results if not x["passed"]),
        }
        document = {
            "startedAt": started,
            "finishedAt": datetime.now(timezone.utc).astimezone().isoformat(),
            "baseUrl": BASE_URL,
            "browser": "Google Chrome headless (CDP)",
            "summary": summary,
            "results": results,
        }
        OUT_PATH.write_text(json.dumps(document, ensure_ascii=False, indent=2), encoding="utf-8")
        print("\nSUMMARY", json.dumps(summary, ensure_ascii=False), f"\nRESULT {OUT_PATH}")
    return 0 if all(x["passed"] for x in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
