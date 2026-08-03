#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""360° 全景查看器 —— 桌面启动器（Windows / 跨平台）.

把纯前端查看器（viewer/）用 pywebview 套壳，并启动一个**迷你本地 HTTP 静态
服务器**线程来托管 viewer 目录与用户选定的全景图。pywebview 加载
``http://127.0.0.1:PORT``，从而规避 ``file://`` 下浏览器对 fetch / 纹理的
跨域限制，同时保证**完全离线**（Three.js 已内置在 viewer/ 中，运行时无任何
外网请求）。

用法::

    # 双击运行（无参数）-> 打开空白查看器，可用「打开图片」或拖拽载入
    pano_viewer.exe
    python pano_viewer_app.py

    # 命令行传入全景图路径 -> 直接载入该图
    pano_viewer.exe "D:/photos/pano.jpg"
    python pano_viewer_app.py "D:/photos/pano.jpg"

离线 / windowed 健壮性：参考姐妹项目 pano-downloader 的 pano_app.py 思路，
本文件自带一套精简版兜底：

* ``ensure_std_streams``：``--windowed`` 构建下 ``sys.stdout/stderr/stdin``
  全为 ``None``，任何 print 都会抛异常，这里补成 devnull 占位；
* ``report_startup_failure``：顶层捕获一切异常，windowed 下用
  ``MessageBoxW`` 弹窗展示异常详情（否则双击会「什么都不发生」）。
"""

from __future__ import annotations

import argparse
import base64
import ctypes
import functools
import mimetypes
import os
import socket
import sys
import threading
import traceback
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from typing import List, Optional

# 保证能 import 到同目录模块（脚本 / 打包后均适用）
_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

# 打包后（PyInstaller onefile）资源在 sys._MEIPASS；开发时取脚本所在目录
if getattr(sys, "frozen", False):
    _BASE = getattr(sys, "_MEIPASS", _HERE)
else:
    _BASE = _HERE

#: viewer 目录（含 index.html / viewer.js / viewer.css / three.min.js）
VIEWER_DIR: str = os.path.join(_BASE, "viewer")

#: 弹窗标题
_FATAL_TITLE: str = "全景查看器启动失败"
_MB_OK_ICONERROR: int = 0x10  # MB_OK | MB_ICONERROR
_MAX_TRACEBACK_CHARS: int = 1800

# --------------------------------------------------------------------------- #
# windowed 健壮性兜底（不依赖 pano-downloader，自包含）
# --------------------------------------------------------------------------- #


def ensure_std_streams() -> None:
    """把值为 None 的标准流补成 devnull 占位。

    ``--windowed`` 构建的进程没有任何标准句柄，CPython 会把 stdout/stderr/stdin
    全部置为 None。此时任何 print / 第三方库写 stderr 都会抛 AttributeError。
    这里只填补 None，不会覆盖已可用的真实流（console 构建下为 no-op）。
    """
    names = ("stdout", "stderr", "stdin", "__stdout__", "__stderr__", "__stdin__")
    if all(getattr(sys, n, None) is not None for n in names):
        return
    try:
        dev_out = open(os.devnull, "w", encoding="utf-8", errors="replace")
        dev_in = open(os.devnull, "r", encoding="utf-8", errors="replace")
    except OSError:
        return
    for name in names:
        if getattr(sys, name, None) is not None:
            continue
        handle = dev_in if name.endswith("stdin") else dev_out
        try:
            setattr(sys, name, handle)
        except (AttributeError, OSError, TypeError):  # pragma: no cover
            pass


def _truncate_middle(text: str, limit: int = _MAX_TRACEBACK_CHARS) -> str:
    """把过长文本从中间截断，保留首尾两段（调用栈首尾信息量最大）。"""
    if limit <= 0 or len(text) <= limit:
        return text
    head = limit // 2
    tail = limit - head
    return f"{text[:head]}\n\n… (中间省略 {len(text) - limit} 字) …\n\n{text[-tail:]}"


def _show_error_dialog(title: str, message: str) -> None:
    """Windows 下弹出一个错误对话框（windowed 构建无 stderr 时的唯一提示手段）。"""
    if os.name != "nt":
        return
    try:
        ctypes.windll.user32.MessageBoxW(0, message, title, _MB_OK_ICONERROR)
    except Exception:  # noqa: BLE001 - 连弹窗都失败就只能放弃
        pass


def report_startup_failure(exc: BaseException) -> None:
    """上报未处理异常，保证任何构建下用户都能看到错误。"""
    detail = traceback.format_exc() or ""
    message = (
        "360° 全景查看器启动失败，程序无法继续运行。\n\n"
        f"异常类型: {type(exc).__name__}\n"
        f"异常信息: {str(exc).strip() or '(无)'}\n\n"
        "调用栈:\n"
        f"{_truncate_middle(detail.strip())}"
    )
    # 先尝试写 stderr；不可用时（windowed）才弹窗
    stream = getattr(sys, "stderr", None)
    if stream is not None and getattr(stream, "name", "") != os.devnull:
        try:
            stream.write(message + "\n")
            stream.flush()
            return
        except Exception:  # noqa: BLE001
            pass
    _show_error_dialog(_FATAL_TITLE, message)


# --------------------------------------------------------------------------- #
# 本地 HTTP 静态服务器
# --------------------------------------------------------------------------- #

#: 当前由命令行 / 文件对话框选定的全景图绝对路径（服务器线程安全读取）
_image_path_holder: List[str] = [""]


def _guess_mime(path: str) -> str:
    mime, _ = mimetypes.guess_type(path)
    return mime or "application/octet-stream"


class PanoHTTPRequestHandler(SimpleHTTPRequestHandler):
    """托管 viewer/ 静态资源；特殊路由 ``/image`` 流式返回当前选定全景图。

    安全策略：
    * 静态资源仅允许访问 VIEWER_DIR 内的文件，做规范化后防目录穿越；
    * ``/image`` 仅返回 _image_path_holder 中记录的文件，不外泄其它路径。
    """

    def __init__(self, *args, **kwargs) -> None:
        # directory= 让父类 SimpleHTTPRequestHandler 以 viewer 为根
        super().__init__(*args, directory=VIEWER_DIR, **kwargs)

    def do_GET(self) -> None:  # noqa: N802 (父类命名)
        path = self.path.split("?", 1)[0].split("#", 1)[0]
        if path == "/image":
            self._serve_current_image()
            return
        # 静态资源：交给父类处理，但先校验路径合法性（防穿越）
        if not self._is_in_viewer_dir(self.translate_path(self.path)):
            self.send_error(403, "Forbidden")
            return
        super().do_GET()

    def _serve_current_image(self) -> None:
        img_path = _image_path_holder[0]
        if not img_path or not os.path.isfile(img_path):
            # 注意：http.server 用 latin-1 严格编码错误信息，中文会抛
            # UnicodeEncodeError 导致连接被断；错误分支必须用 ASCII 安全文案。
            self.send_error(404, "No panorama image selected")
            return
        try:
            with open(img_path, "rb") as fh:
                data = fh.read()
        except OSError as exc:
            self.send_error(500, "Failed to read image: " + str(exc).encode("ascii", "replace").decode("ascii"))
            return
        self.send_response(200)
        self.send_header("Content-Type", _guess_mime(img_path))
        self.send_header("Content-Length", str(len(data)))
        # 同源（127.0.0.1）下其实不需要，但加上更稳妥
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    @staticmethod
    def _is_in_viewer_dir(fs_path: str) -> bool:
        try:
            fs_path = os.path.normpath(os.path.abspath(fs_path))
            root = os.path.normpath(os.path.abspath(VIEWER_DIR))
            return os.path.commonpath([fs_path, root]) == root
        except ValueError:
            return False

    def log_message(self, *args) -> None:  # 静默日志，避免干扰
        pass


def find_free_port() -> int:
    """在 127.0.0.1 上找一个空闲端口（存在极小竞态，本地够用）。"""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def start_server(port: int) -> ThreadingHTTPServer:
    """启动本地静态服务器线程（守护线程）。"""
    httpd = ThreadingHTTPServer(("127.0.0.1", port), PanoHTTPRequestHandler)
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    return httpd


# --------------------------------------------------------------------------- #
# pywebview 桥接（让网页内的「打开图片」按钮可调起系统原生文件对话框）
# --------------------------------------------------------------------------- #


class PanoAPI:
    """暴露给 JS 的 Python API（pywebview 自动注入为 window.pywebview.api）。"""

    def __init__(self, window_holder: List) -> None:
        self._window_holder = window_holder

    def open_file_dialog(self) -> Optional[str]:
        """弹出系统文件对话框；选中后更新 /image 路由并通知网页重新加载。"""
        import webview  # pywebview 的别名，确保已安装

        window = self._window_holder[0]
        if window is None:
            return None
        result = window.create_file_dialog(
            webview.OPEN_DIALOG,
            file_types=(("全景图片", "*.jpg;*.jpeg;*.png;*.bmp;*.tif;*.tiff"),),
        )
        if not result:
            return None
        path = result[0]
        _image_path_holder[0] = path
        try:
            window.evaluate_js("window.PanoViewer && window.PanoViewer.loadImageUrl('/image')")
        except Exception:  # noqa: BLE001 - 对话框在非主线程也可能触发，忽略失败
            pass
        return path


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="360° 等距圆柱全景查看器（桌面版）",
    )
    parser.add_argument(
        "image",
        nargs="?",
        default=None,
        help="可选：启动即载入的等距圆柱全景图路径（JPG 等）",
    )
    return parser


def main(argv: Optional[List[str]] = None) -> int:
    ensure_std_streams()

    args = build_parser().parse_args(sys.argv[1:] if argv is None else argv)

    if not os.path.isdir(VIEWER_DIR):
        raise RuntimeError(f"找不到 viewer 目录：{VIEWER_DIR}")

    # 选定端口并构造启动 URL；若命令行传入图片，记录到路由并附加 ?image= 参数
    port = find_free_port()
    start_url = f"http://127.0.0.1:{port}/"
    if args.image:
        abs_img = os.path.abspath(args.image)
        if not os.path.isfile(abs_img):
            raise RuntimeError(f"指定的图片不存在：{abs_img}")
        _image_path_holder[0] = abs_img
        start_url = f"http://127.0.0.1:{port}/?image=/image"

    import webview  # 延迟导入，缺依赖时异常更清晰

    httpd = start_server(port)
    window_holder: List = [None]
    api = PanoAPI(window_holder)

    window = webview.create_window(
        "360° 全景查看器",
        url=start_url,
        js_api=api,
        width=1280,
        height=800,
        min_size=(640, 480),
    )
    window_holder[0] = window

    # 注意：若命令行未传图片，pywebview 的窗口内「打开图片」按钮会调起
    # 原生对话框（PanoAPI.open_file_dialog）；用户也可直接拖拽 / 用网页文件选择器。
    webview.start()
    httpd.shutdown()
    return 0


def entry(argv: Optional[List[str]] = None) -> int:
    """带顶层异常兜底的进程入口。"""
    try:
        return main(argv)
    except SystemExit:
        raise
    except KeyboardInterrupt:
        return 130
    except BaseException as exc:  # noqa: BLE001 - 顶层兜底必须捕获一切
        try:
            ensure_std_streams()
            report_startup_failure(exc)
        except Exception:  # noqa: BLE001
            pass
        return 3


if __name__ == "__main__":
    raise SystemExit(entry())
