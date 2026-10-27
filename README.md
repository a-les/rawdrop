<p align="center">
  <img src="static/logo.png" width="96" alt="RawDrop Logo" />
</p>

<h1 align="center">RawDrop</h1>

<p align="center">
  Standalone Windows desktop application for downloading TikTok videos in original quality without watermarks.
</p>

<p align="center">
  <a href="#english">English</a> • <a href="#русский">Русский</a>
</p>

---

<a name="english"></a>
## English

### What is RawDrop?
**RawDrop** is a standalone Windows desktop utility for downloading TikTok videos in original source quality without watermarks or re-encoding.

### Key Benefits
* **Original Quality (No Watermark):** Directly captures the source CDN stream up to 1080p without compression or quality loss.
* **Fully Standalone:** Single `.exe` binary. Does not require Python, browser extensions, or additional dependencies.
* **Fast & Direct:** No ads, subscriptions, or third-party web converters.
* **Secure & Local:** Operates entirely on your local machine (`127.0.0.1`), with strict domain white-listing and path isolation.

### Installation & Usage
1. Download the latest `RawDrop.exe` from [Releases](https://github.com/inf04hex/rawdrop/releases).
2. Run `RawDrop.exe`.
3. Paste a TikTok URL (supports standard and short `vt.tiktok.com` links) and click **Download**. Videos are saved into the `downloads/` directory next to the application.

#### Run from Source (Optional)
```bash
git clone https://github.com/inf04hex/rawdrop.git
cd rawdrop
pip install -r requirements.txt
python run_desktop.py
```

---

<a name="русский"></a>
## Русский

### Что такое RawDrop?
**RawDrop** — настольное Windows-приложение для прямого скачивания видео из TikTok в оригинальном качестве без водяных знаков и пережатия.

### Преимущества
* **Оригинальное качество без водяного знака:** Захват чистого потока напрямую с CDN-серверов в исходном разрешении (вплоть до 1080p).
* **Полная автономность:** Единый исполняемый файл `.exe`. Не требует Python, расширений для браузера и сторонних библиотек.
* **Быстро и без рекламы:** Без лимитов, подписок и сомнительных сайтов-конвертеров.
* **Безопасно:** Работает строго локально на вашем компьютере, с фильтрацией ссылок и изоляцией папки загрузок.

### Установка и запуск
1. Скачайте `RawDrop.exe` из раздела [Releases](https://github.com/inf04hex/rawdrop/releases).
2. Запустите `RawDrop.exe`.
3. Вставьте ссылку на видео (поддерживаются как полные ссылки, так и короткие `vt.tiktok.com`) и нажмите **Download**. Видео сохранится в папку `downloads/` рядом с программой.

#### Запуск из исходного кода (для разработчиков)
```bash
git clone https://github.com/inf04hex/rawdrop.git
cd rawdrop
pip install -r requirements.txt
python run_desktop.py
```

---

## License & Author
* **License:** [MIT](LICENSE)
* **Author:** [@inf04hex](https://github.com/inf04hex)
