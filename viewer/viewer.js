/* =========================================================================
 * viewer.js — 360° 等距圆柱（equirectangular）全景查看器核心逻辑
 *
 * 渲染原理：
 *   1. 把等距圆柱图贴到一个「朝内」的球体（SphereGeometry + scale(-1,1,1) 翻转
 *      法线，使正面朝向球心，无需 BackSide 也能从内部看到纹理，且不会发生左右镜像）。
 *   2. 透视相机置于球心 (0,0,0)；通过 yaw(经度) / pitch(纬度) 计算一个注视目标点，
 *      每帧 camera.lookAt(target) 实现环视。
 *   3. 滚轮改变 camera.fov 实现缩放；自动旋转时每帧令 yaw 自增。
 *
 * 依赖：本地内置 three.min.js（UMD 全局 THREE，离线可用）。
 * 兼容：桌面浏览器 / pywebview(exe) / Android WebView，均通过同一套文件运行。
 * ========================================================================= */
(function () {
  "use strict";

  /* ----------------------------- 默认参数 ----------------------------- */
  var DEFAULTS = {
    fov: 75, // 初始视场角（度）
    minFov: 30, // 最大放大（FOV 越小越「近」）
    maxFov: 100, // 最小放大（FOV 越大越「远」）
    autoRotateSpeed: 0.03, // 自动旋转速度（度/帧，约 60fps 基准）
    minLat: -85, // 俯仰角下限（避免越过两极导致画面翻转）
    maxLat: 85, // 俯仰角上限
    sphereRadius: 500, // 球半径（仅影响坐标尺度，不影响观感）
    dragSpeed: 0.1, // 拖拽灵敏度（度/像素）
    wheelSpeed: 0.05 // 滚轮缩放灵敏度（FOV/滚轮单位）
  };

  /* 把数值限制在 [min, max] 区间内 */
  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  /* 生成一张占位纹理（未加载图片时显示，给出操作提示） */
  function makePlaceholderTexture() {
    var canvas = document.createElement("canvas");
    canvas.width = 2048;
    canvas.height = 1024;
    var ctx = canvas.getContext("2d");
    // 深色径向渐变背景
    var grad = ctx.createLinearGradient(0, 0, 0, canvas.height);
    grad.addColorStop(0, "#0b0f17");
    grad.addColorStop(1, "#05070b");
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    // 提示文字
    ctx.fillStyle = "rgba(255,255,255,0.85)";
    ctx.textAlign = "center";
    ctx.font = "bold 64px sans-serif";
    ctx.fillText("360° 全景查看器", canvas.width / 2, canvas.height / 2 - 20);
    ctx.font = "32px sans-serif";
    ctx.fillStyle = "rgba(255,255,255,0.55)";
    ctx.fillText("拖拽图片到此处，或点击「打开图片」", canvas.width / 2, canvas.height / 2 + 50);
    var tex = new THREE.Texture(canvas);
    tex.needsUpdate = true;
    return tex;
  }

  /* ============================ 查看器类 ============================ */
  function PanoViewer(container, options) {
    if (typeof THREE === "undefined") {
      this._fatal("three.min.js 未加载，请确认 viewer 目录下存在该文件（需离线内置 Three.js）。");
      return;
    }
    this.container = container;
    this.opts = Object.assign({}, DEFAULTS, options || {});

    // 视角状态
    this.lon = 0; // 经度（yaw，度）
    this.lat = 0; // 纬度（pitch，度）
    this.autoRotate = false;

    // 拖拽交互状态
    this._dragging = false;
    this._pointerStart = { x: 0, y: 0 };
    this._lonStart = 0;
    this._latStart = 0;

    // 触摸双指缩放状态
    this._pinchStartDist = 0;
    this._pinchStartFov = this.opts.fov;

    this._currentTexture = null;
    this._target = new THREE.Vector3();
    this._lastFrame = performance.now();
    this._rafId = null;

    this._initThree();
    this._bindEvents();

    var self = this;
    this._loop = this._loop.bind(this);
    this._rafId = requestAnimationFrame(this._loop);

    // 若 URL 带 ?image=<url>，自动加载（桌面 exe 的本地服务器场景）
    this._loadFromQueryParam();
  }

  /* ---------------------- Three.js 场景初始化 ---------------------- */
  PanoViewer.prototype._initThree = function () {
    var w = this.container.clientWidth || window.innerWidth;
    var h = this.container.clientHeight || window.innerHeight;

    this.renderer = new THREE.WebGLRenderer({ antialias: true });
    this.renderer.setPixelRatio(window.devicePixelRatio || 1);
    this.renderer.setSize(w, h);
    this.container.appendChild(this.renderer.domElement);

    this.scene = new THREE.Scene();

    this.camera = new THREE.PerspectiveCamera(this.opts.fov, w / h, 0.1, 1100);
    this.camera.position.set(0, 0, 0); // 相机置于球心

    // 球体：半径 R，经纬细分足够细腻以避免大图出现棱角
    var geometry = new THREE.SphereGeometry(this.opts.sphereRadius, 64, 48);
    // 翻转 X 轴使法线朝内：从球心观察时看到的是「正面」，且左右不镜像
    geometry.scale(-1, 1, 1);

    this._placeholder = makePlaceholderTexture();
    this.material = new THREE.MeshBasicMaterial({ map: this._placeholder });
    this.mesh = new THREE.Mesh(geometry, this.material);
    this.scene.add(this.mesh);
  };

  /* ------------------------- 事件绑定 ------------------------- */
  PanoViewer.prototype._bindEvents = function () {
    var el = this.renderer.domElement;
    var self = this;

    // 指针（鼠标 / 触摸 / 笔）统一用 Pointer Events 处理
    el.addEventListener("pointerdown", function (e) {
      self._dragging = true;
      self._pointerStart.x = e.clientX;
      self._pointerStart.y = e.clientY;
      self._lonStart = self.lon;
      self._latStart = self.lat;
      el.setPointerCapture && el.setPointerCapture(e.pointerId);
    });

    el.addEventListener("pointermove", function (e) {
      if (!self._dragging) return;
      var dx = e.clientX - self._pointerStart.x;
      var dy = e.clientY - self._pointerStart.y;
      // 拖动改变 yaw / pitch（横向改经度，纵向改纬度）
      self.lon = self._lonStart - dx * self.opts.dragSpeed;
      self.lat = clamp(self._latStart + dy * self.opts.dragSpeed, self.opts.minLat, self.opts.maxLat);
    });

    function endDrag(e) {
      self._dragging = false;
      el.releasePointerCapture && e && e.pointerId != null && el.releasePointerCapture(e.pointerId);
    }
    el.addEventListener("pointerup", endDrag);
    el.addEventListener("pointercancel", endDrag);
    el.addEventListener("pointerleave", endDrag);

    // 滚轮缩放（改变 FOV）
    el.addEventListener(
      "wheel",
      function (e) {
        e.preventDefault();
        self.zoom(e.deltaY * self.opts.wheelSpeed);
      },
      { passive: false }
    );

    // 触摸双指捏合缩放（移动端）
    el.addEventListener("touchstart", function (e) {
      if (e.touches.length === 2) {
        self._pinchStartDist = self._touchDist(e.touches);
        self._pinchStartFov = self.opts.fov;
      }
    }, { passive: true });

    el.addEventListener("touchmove", function (e) {
      if (e.touches.length === 2 && self._pinchStartDist > 0) {
        e.preventDefault();
        var d = self._touchDist(e.touches);
        // 双指张开 -> 距离变大 -> FOV 变小（放大）
        var ratio = self._pinchStartDist / Math.max(1, d);
        self.setFov(self._pinchStartFov * ratio);
      }
    }, { passive: false });

    el.addEventListener("touchend", function () {
      self._pinchStartDist = 0;
    });

    // 窗口 / 容器尺寸变化
    var onResize = function () {
      self.resize();
    };
    window.addEventListener("resize", onResize);
    if (typeof ResizeObserver !== "undefined") {
      this._ro = new ResizeObserver(onResize);
      this._ro.observe(this.container);
    }
  };

  PanoViewer.prototype._touchDist = function (touches) {
    var dx = touches[0].clientX - touches[1].clientX;
    var dy = touches[0].clientY - touches[1].clientY;
    return Math.sqrt(dx * dx + dy * dy);
  };

  /* ---------------------- 渲染主循环 ---------------------- */
  PanoViewer.prototype._loop = function () {
    var now = performance.now();
    var dt = (now - this._lastFrame) / 16.6667; // 归一化到 60fps 基准
    this._lastFrame = now;

    if (this.autoRotate) {
      this.lon += this.opts.autoRotateSpeed * dt;
    }

    // 由 yaw/pitch 计算球面上的注视点，相机始终看向该点
    var phi = THREE.MathUtils.degToRad(90 - this.lat); // 纬度 -> 极角
    var theta = THREE.MathUtils.degToRad(this.lon); // 经度 -> 方位角
    var r = this.opts.sphereRadius;
    this._target.set(
      r * Math.sin(phi) * Math.cos(theta),
      r * Math.cos(phi),
      r * Math.sin(phi) * Math.sin(theta)
    );
    this.camera.lookAt(this._target);

    this.renderer.render(this.scene, this.camera);
    this._rafId = requestAnimationFrame(this._loop);
  };

  /* ---------------------- 图片加载接口 ---------------------- */

  // 通过 URL 加载（桌面 exe 本地服务器场景，同源无跨域问题）
  PanoViewer.prototype.loadImageUrl = function (url) {
    var self = this;
    this._setStatus("正在加载图片…");
    new THREE.TextureLoader().load(
      url,
      function (tex) {
        tex.colorSpace = THREE.SRGBColorSpace;
        tex.minFilter = THREE.LinearFilter; // 兼容非 2 的幂尺寸
        tex.generateMipmaps = false;
        self._applyTexture(tex);
        self._setStatus("", false);
      },
      undefined,
      function () {
        self._setStatus("图片加载失败：" + url, true);
      }
    );
  };

  // 通过 dataURL 加载（浏览器/WebView 内 <input> 或拖拽读取，完全离线）
  PanoViewer.prototype.loadImageDataURL = function (dataURL) {
    var self = this;
    this._setStatus("正在加载图片…");
    var img = new Image();
    img.onload = function () {
      var tex = new THREE.Texture(img);
      tex.colorSpace = THREE.SRGBColorSpace;
      tex.minFilter = THREE.LinearFilter;
      tex.generateMipmaps = false;
      tex.needsUpdate = true;
      self._applyTexture(tex);
      self._setStatus("", false);
    };
    img.onerror = function () {
      self._setStatus("图片解析失败", true);
    };
    img.src = dataURL;
  };

  // 统一替换纹理并释放旧纹理，避免显存泄漏
  PanoViewer.prototype._applyTexture = function (tex) {
    if (this._currentTexture) {
      this._currentTexture.dispose();
    } else if (this._placeholder) {
      this._placeholder.dispose();
    }
    this._placeholder = null;
    this._currentTexture = tex;
    this.material.map = tex;
    this.material.needsUpdate = true;
    // 首次成功加载后隐藏提示层
    var hint = document.getElementById("drop-hint");
    if (hint) hint.classList.add("hidden");
  };

  /* ---------------------- 公开控制方法 ---------------------- */

  // 缩放：deltaFov 为正 -> FOV 增大（拉远）
  PanoViewer.prototype.zoom = function (deltaFov) {
    this.setFov(this.opts.fov + deltaFov);
  };

  PanoViewer.prototype.setFov = function (fov) {
    this.opts.fov = clamp(fov, this.opts.minFov, this.opts.maxFov);
    this.camera.fov = this.opts.fov;
    this.camera.updateProjectionMatrix();
    this._emitFov();
  };

  PanoViewer.prototype.setAutoRotate = function (on) {
    this.autoRotate = !!on;
    if (typeof window !== "undefined" && window._panoSyncAutoRotate) {
      window._panoSyncAutoRotate(this.autoRotate);
    }
  };

  PanoViewer.prototype.toggleAutoRotate = function () {
    this.setAutoRotate(!this.autoRotate);
    return this.autoRotate;
  };

  PanoViewer.prototype.toggleFullscreen = function () {
    var doc = document;
    if (!doc.fullscreenElement) {
      (doc.documentElement.requestFullscreen || doc.documentElement.webkitRequestFullscreen).call(
        doc.documentElement
      );
    } else {
      (doc.exitFullscreen || doc.webkitExitFullscreen).call(doc);
    }
  };

  PanoViewer.prototype.reset = function () {
    this.lon = 0;
    this.lat = 0;
    this.setFov(DEFAULTS.fov);
  };

  PanoViewer.prototype.resize = function () {
    var w = this.container.clientWidth || window.innerWidth;
    var h = this.container.clientHeight || window.innerHeight;
    if (w === 0 || h === 0) return;
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(w, h);
  };

  PanoViewer.prototype.dispose = function () {
    if (this._rafId) cancelAnimationFrame(this._rafId);
    if (this._ro) this._ro.disconnect();
    if (this._currentTexture) this._currentTexture.dispose();
    if (this._placeholder) this._placeholder.dispose();
    this.renderer.dispose();
  };

  /* ---------------------- 内部辅助 ---------------------- */
  PanoViewer.prototype._setStatus = function (msg, isError) {
    var toast = document.getElementById("status-toast");
    if (!toast) return;
    if (!msg) {
      toast.hidden = true;
      return;
    }
    toast.textContent = msg;
    toast.hidden = false;
    toast.classList.toggle("error", !!isError);
  };

  PanoViewer.prototype._emitFov = function () {
    var el = document.getElementById("fov-readout");
    if (el) el.textContent = "FOV " + Math.round(this.opts.fov) + "°";
  };

  PanoViewer.prototype._fatal = function (msg) {
    var hint = document.getElementById("drop-hint");
    if (hint) hint.querySelector(".hint-sub").textContent = msg;
  };

  // 读取 URL 中的 ?image= 参数并加载（桌面 exe 通过本地服务器传入）
  PanoViewer.prototype._loadFromQueryParam = function () {
    try {
      var params = new URLSearchParams(window.location.search);
      var url = params.get("image");
      if (url) this.loadImageUrl(url);
    } catch (e) {
      /* 忽略：无参数时正常显示占位图 */
    }
  };

  /* ============================ 启动与 UI 绑定 ============================ */
  function readFileAsDataURL(file, cb) {
    var reader = new FileReader();
    reader.onload = function () {
      cb(reader.result);
    };
    reader.onerror = function () {
      cb(null);
    };
    reader.readAsDataURL(file);
  }

  var _booted = false;
  function boot() {
    if (_booted) return; // 防止 DOMContentLoaded 与 pywebviewready 重复触发
    _booted = true;
    var container = document.getElementById("pano-container");
    if (!container) return;
    var viewer = new PanoViewer(container);
    if (!viewer.renderer) return; // 初始化失败（如 THREE 缺失）

    // 暴露到全局，供桌面 / Android 桥接调用
    window.PanoViewer = viewer;

    // ---- 文件选择器 ----
    var fileInput = document.getElementById("file-input");
    fileInput.addEventListener("change", function () {
      var file = fileInput.files && fileInput.files[0];
      if (!file) return;
      readFileAsDataURL(file, function (dataURL) {
        if (dataURL) viewer.loadImageDataURL(dataURL);
      });
      fileInput.value = ""; // 允许重复选择同一文件
    });

    // ---- 「打开图片」按钮：优先用原生对话框（pywebview/Android 桥接），否则回退到 <input> ----
    var openBtn = document.getElementById("btn-open");
    openBtn.addEventListener("click", function () {
      if (window.pywebview && window.pywebview.api && window.pywebview.api.open_file_dialog) {
        window.pywebview.api.open_file_dialog();
      } else {
        fileInput.click();
      }
    });

    // ---- 自动旋转 ----
    var autoBtn = document.getElementById("btn-autorotate");
    autoBtn.addEventListener("click", function () {
      var on = viewer.toggleAutoRotate();
      autoBtn.classList.toggle("active", on);
    });
    // 让 Python 端改变自动旋转时同步按钮高亮
    window._panoSyncAutoRotate = function (on) {
      autoBtn.classList.toggle("active", on);
    };

    // ---- 全屏 ----
    document.getElementById("btn-fullscreen").addEventListener("click", function () {
      viewer.toggleFullscreen();
    });

    // ---- 复位 ----
    document.getElementById("btn-reset").addEventListener("click", function () {
      viewer.reset();
    });

    // ---- 拖拽打开 ----
    var dragDepth = 0;
    window.addEventListener("dragenter", function (e) {
      e.preventDefault();
      dragDepth++;
      container.classList.add("drag-over");
    });
    window.addEventListener("dragover", function (e) {
      e.preventDefault();
    });
    window.addEventListener("dragleave", function (e) {
      e.preventDefault();
      dragDepth = Math.max(0, dragDepth - 1);
      if (dragDepth === 0) container.classList.remove("drag-over");
    });
    window.addEventListener("drop", function (e) {
      e.preventDefault();
      dragDepth = 0;
      container.classList.remove("drag-over");
      var file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
      if (!file || !file.type || file.type.indexOf("image") !== 0) return;
      readFileAsDataURL(file, function (dataURL) {
        if (dataURL) viewer.loadImageDataURL(dataURL);
      });
    });

    // ---- 工具栏空闲自动隐藏（移动端/沉浸观看） ----
    var toolbar = document.getElementById("toolbar");
    var idleTimer = null;
    function wake() {
      toolbar.classList.remove("idle");
      if (idleTimer) clearTimeout(idleTimer);
      idleTimer = setTimeout(function () {
        toolbar.classList.add("idle");
      }, 3000);
    }
    ["mousemove", "pointerdown", "keydown", "touchstart", "wheel"].forEach(function (evt) {
      window.addEventListener(evt, wake, { passive: true });
    });
    wake();

    // ---- 键盘快捷键：方向键环视，+/- 缩放，空格自动旋转，F 全屏，R 复位 ----
    window.addEventListener("keydown", function (e) {
      var step = 5;
      if (e.key === "ArrowLeft") viewer.lon -= step;
      else if (e.key === "ArrowRight") viewer.lon += step;
      else if (e.key === "ArrowUp") viewer.lat = clamp(viewer.lat + step, viewer.opts.minLat, viewer.opts.maxLat);
      else if (e.key === "ArrowDown") viewer.lat = clamp(viewer.lat - step, viewer.opts.minLat, viewer.opts.maxLat);
      else if (e.key === "+" || e.key === "=") viewer.zoom(-4);
      else if (e.key === "-" || e.key === "_") viewer.zoom(4);
      else if (e.key === " ") {
        e.preventDefault();
        autoBtn.click();
      } else if (e.key === "f" || e.key === "F") viewer.toggleFullscreen();
      else if (e.key === "r" || e.key === "R") viewer.reset();
    });
  }

  // pywebview 注入就绪事件 / DOMContentLoaded 均可触发启动
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
  // 某些 WebView 环境下补充监听 pywebviewready
  window.addEventListener("pywebviewready", boot);
})();
