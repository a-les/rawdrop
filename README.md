<p align="center">
  <img src="static/logo.png" width="120" alt="RawDrop Logo" style="border-radius: 24px;" />
</p>

<h1 align="center">⚡ RawDrop — TikTok Stream Extractor</h1>

<p align="center">
  <b>Clean, standalone Windows desktop & web client for direct extraction of uncompressed TikTok CDN video streams without watermarks.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Windows-0078D6?style=flat-square&logo=windows" alt="Platform" />
  <img src="https://img.shields.io/badge/Python-3.10+-3776AB?style=flat-square&logo=python&logoColor=white" alt="Python" />
  <img src="https://img.shields.io/badge/Framework-FastAPI%20%2B%20pywebview-05998B?style=flat-square" alt="Framework" />
  <img src="https://img.shields.io/badge/License-MIT-F15025?style=flat-square" alt="License" />
  <img src="https://img.shields.io/badge/Author-%40inf04hex-E6E8E6?style=flat-square&logo=github" alt="Author" />
</p>

<p align="center">
  <a href="#english">English</a> • <a href="#русский">Русский</a>
</p>

---

<a name="english"></a>
## 🇬🇧 English

### Overview
**RawDrop** is a lightweight, high-performance desktop utility built for extracting raw, original-bitrate MP4 streams directly from TikTok's CDN servers. Unlike common web scrapers that re-encode or compress video files, RawDrop hooks directly into the resolver pipeline to capture source 1080p/HEVC/H.264 video streams completely free of watermarks or overlays.

Built with a Bauhaus-inspired minimal dark UI, it offers fixed-aspect desktop ergonomics, real-time SSE progress pipelines, and enterprise-grade local security.

### ✨ Key Features
* 🚀 **Zero Re-encoding / Pure Source:** Downloads original stream data directly from the CDN with no quality degradation and no TikTok watermark.
* ⚡ **Universal URL Resolver:** Instant resolution for both desktop links (`tiktok.com/@user/video/...`) and short links (`vt.tiktok.com/...`, `vm.tiktok.com/...`).
* 📊 **Live Stream Pipeline:** Real-time Server-Sent Events (SSE) telemetry visualization across 5 discrete processing nodes (`Source` ➔ `Resolver` ➔ `CDN Stream` ➔ `Verify` ➔ `Storage`).
* 🎨 **Bauhaus / Minimal Contrast UI:**
  * Clean dark palette (`#121212`, flame accent `#F15025`, alabaster `#E6E8E6`).
  * Fixed window dimensions with ergonomic proportions (1080 × 720).
  * Floating bottom input pod with one-click clipboard parsing.
  * Interactive vertical download history with direct file preview and folder reveal.
* 🛡️ **Engineered Security:**
  * **Strict SSRF Mitigation:** Domain white-listing restricted exclusively to verified TikTok endpoints.
  * **Path Traversal Protection:** Absolute path sandboxing locked to the `downloads/` directory.
  * **Network Isolation:** HTTP backend strictly bound to loopback `127.0.0.1` — no external network listening.

---

### 🚀 Getting Started

#### Option 1: Standalone Binary (No Python required)
1. Download the latest `RawDrop.exe` from the [Releases](https://github.com/inf04hex/rawdrop/releases) section.
2. Launch `RawDrop.exe`. Videos are saved automatically in the `downloads/` folder next to the executable.

#### Option 2: Running from Source
```bash
# 1. Clone repository
git clone https://github.com/inf04hex/rawdrop.git
cd rawdrop

# 2. Create and activate virtual environment
python -m venv venv
venv\Scripts\activate   # Windows

# 3. Install dependencies
pip install -r requirements.txt

# 4. Launch Desktop App
python run_desktop.py
```

*For browser-only web mode:*
```bash
run_web.bat
# or: uvicorn server:app --host 127.0.0.1 --port 8000
```

---

### 🛠️ Building Standalone Executable
To compile your own single-file `.exe` package using PyInstaller:
```bash
venv\Scripts\pyinstaller.exe --noconfirm --clean RawDrop.spec
```
The resulting `RawDrop.exe` will be generated in `dist/`.

---

<a name="русский"></a>
## 🇷🇺 Русский

### Описание
**RawDrop** — быстрое и автономное десктопное приложение для Windows, предназначенное для прямого захвата оригинального видеопотока из TikTok без водяных знаков и пережатия качества. Программа работает напрямую с CDN-серверами, извлекая оригинальный файл в максимальном разрешении (вплоть до 1080p).

Интерфейс спроектирован в стиле минималистичного контрастного Bauhaus-дизайна с фиксированными десктопными пропорциями, живым графом пайплайна загрузки и историей скачиваний.

### ✨ Основные возможности
* 🚀 **Оригинальный поток (No Watermark):** Прямое извлечение чистого H.264/HEVC потока с CDN TikTok без потери битрейта.
* ⚡ **Мгновенный парсинг:** Поддержка коротких ссылок (`vt.tiktok.com`, `vm.tiktok.com`) и обычных веб-ссылок.
* 📊 **Интерактивный граф пайплайна:** Отображение стадий обработки в реальном времени через Server-Sent Events (SSE).
* 🎨 **Продуманный интерфейс:**
  * Тёмная контрастная тема (`#121212`, `#F15025`, `#E6E8E6`).
  * Фиксированный размер окна (1080 × 720).
  * Плавающий нижний блок ввода со вставкой из буфера обмена в один клик.
  * История загрузок с возможностью быстрого открытия файла и перехода в папку.
* 🛡️ **Безопасность:**
  * Защита от SSRF (строгий белый список доменов TikTok).
  * Защита от Path Traversal и произвольного исполнения (изоляция в папке `downloads/`).
  * Полная сетевая изоляция (привязка исключительно к локальной петле `127.0.0.1`).

---

### 🚀 Запуск и использование

#### Вариант 1: Готовый файл (Без установки Python)
1. Скачайте `RawDrop.exe` из раздела [Releases](https://github.com/inf04hex/rawdrop/releases).
2. Запустите файл. Скачанные видео сохраняются в папку `downloads/` рядом с программой.

#### Вариант 2: Запуск из исходного кода
```bash
# 1. Клонировать репозиторий
git clone https://github.com/inf04hex/rawdrop.git
cd rawdrop

# 2. Создать и активировать окружение
python -m venv venv
venv\Scripts\activate

# 3. Установить зависимости
pip install -r requirements.txt

# 4. Запустить настольную версию
python run_desktop.py
```

---

### 🛠️ Сборка в .exe
Для самостоятельной сборки исполняемого файла:
```bash
venv\Scripts\pyinstaller.exe --noconfirm --clean RawDrop.spec
```
Итоговый бинарник появится в директории `dist/RawDrop.exe`.

---

## 📄 License
Распространяется под свободной лицензией [MIT](LICENSE).

## 👤 Author
* **Developer:** [@inf04hex](https://github.com/inf04hex)
