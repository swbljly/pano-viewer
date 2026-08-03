#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把全景查看器桌面启动器打包为离线 Windows exe（PyInstaller onefile --windowed）.

用法::

    # 安装打包工具
    pip install pyinstaller

    # 执行打包（产物在 dist/360全景查看器.exe）
    python build_viewer_exe.py

设计要点（对齐姐妹项目 pano-downloader 的打包经验）：

* ``--onefile --windowed``：单文件、无控制台窗口（双击零黑框）。
* ``--add-data viewer;viewer``：把 viewer/（含 three.min.js）一并打进 exe，
  运行时解压到 ``sys._MEIPASS/viewer``，确保 Three.js 完全离线内置。
* ``hiddenimports``：pywebview / webview / threading / http.server 等均为运行期
  才触发 import，必须显式声明，否则打包后运行报 ModuleNotFoundError。
* ``--noconsole`` 即 ``--windowed``；windowed 下的标准流兜底已在 pano_viewer_app.py
  内处理（devnull 占位 + MessageBoxW 顶层异常弹窗）。
"""

from __future__ import annotations

import os
import sys

# PyInstaller 主入口（编程式调用，等价于命令行）
# 新版 PyInstaller 的公开入口在 PyInstaller.__main__.run
from PyInstaller.__main__ import run as pyinstaller_main

# 资源 / 脚本路径（相对本文件）
_HERE = os.path.dirname(os.path.abspath(__file__))
APP_SCRIPT = os.path.join(_HERE, "pano_viewer_app.py")
VIEWER_DIR = os.path.join(_HERE, "viewer")

EXE_NAME = "360全景查看器"
DIST_DIR = os.path.join(_HERE, "dist")


def build() -> None:
    if not os.path.isfile(APP_SCRIPT):
        raise FileNotFoundError(f"找不到启动脚本：{APP_SCRIPT}")
    if not os.path.isdir(VIEWER_DIR):
        raise FileNotFoundError(f"找不到 viewer 目录：{VIEWER_DIR}")

    # Windows 用分号分隔的 SRC;DEST；其它平台用冒号
    add_data_sep = ";" if os.name == "nt" else ":"
    add_data = f"{VIEWER_DIR}{add_data_sep}viewer"

    pyinstaller_args = [
        APP_SCRIPT,
        "--name", EXE_NAME,
        "--onefile",
        "--windowed",
        "--noconfirm",
        "--clean",
        f"--distpath={DIST_DIR}",
        f"--add-data={add_data}",
        "--hidden-import=pywebview",
        "--hidden-import=webview",
        "--hidden-import=webview.platforms",
        "--hidden-import=webview.platforms.cef",
        "--hidden-import=webview.platforms.edgechromium",
        "--hidden-import=webview.platforms.mshtml",
        "--hidden-import=webview.platforms.gtk",
        "--hidden-import=webview.platforms.cocoa",
        "--hidden-import=webview.platforms.qt",
        "--hidden-import=threading",
        "--hidden-import=http.server",
        "--hidden-import=socket",
        "--hidden-import=ctypes",
        # 加密启动时也把标准库带上，确保 windowed 下 devnull / 弹窗可用
        "--hidden-import=base64",
        "--hidden-import=mimetypes",
    ]

    print(f"[build] 开始打包：{EXE_NAME}.exe")
    print(f"[build] 启动脚本：{APP_SCRIPT}")
    print(f"[build] viewer 目录：{VIEWER_DIR}")
    pyinstaller_main(pyinstaller_args)
    print(f"[build] 完成。产物位于：{os.path.join(DIST_DIR, EXE_NAME + '.exe')}")


if __name__ == "__main__":
    try:
        build()
    except KeyboardInterrupt:
        print("\n[build] 用户中断。")
        sys.exit(130)
    except Exception as exc:  # noqa: BLE001
        print(f"[build] 打包失败：{exc}", file=sys.stderr)
        sys.exit(1)
