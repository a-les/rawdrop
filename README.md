<p align="center">
  <img src="static/logo.png" width="96" alt="RawDrop Logo" />
</p>

<h1 align="center">RawDrop</h1>

<p align="center">
  Standalone Windows desktop and Android mobile application for downloading TikTok videos in original quality without watermarks.
</p>

<p align="center">
  <a href="#english">English</a> • <a href="#русский">Русский</a>
</p>

---

<a name="english"></a>
## English

### What is RawDrop?
**RawDrop** is a standalone utility (Windows Desktop + Android Mobile) for downloading TikTok videos in original source quality without watermarks or re-encoding.

### Key Benefits
* **Original Quality (No Watermark):** Directly captures the source CDN stream up to 1080p without compression or quality loss.
* **Fully Standalone:** Single `.exe` binary on Windows and standalone `.apk` on Android. No subscriptions, external servers, or browser extensions required.
* **Fast & Direct:** Direct local downloads with zero advertising or third-party web converters.
* **Android Share-Target:** Share any video directly from the official TikTok app to RawDrop for instant background downloading to Gallery (`Movies/RawDrop`).
* **Secure & Local:** Operates entirely on your local machine / device with strict link verification.

### Installation & Usage

#### 💻 Windows Desktop
1. Download `RawDrop.exe` from [Releases](https://github.com/a-les/rawdrop/releases).
2. Run `RawDrop.exe`.
3. Paste a TikTok URL (supports standard and short `vt.tiktok.com` links) and click **Download**. Videos are saved into the `downloads/` directory next to the application.

#### 📱 Android App
1. Download `RawDrop.apk` from [Releases](https://github.com/a-les/rawdrop/releases) or the Actions build tab.
2. Install the `.apk` on your phone.
3. Use in-app or simply tap **Share ➔ RawDrop** inside TikTok. The video saves automatically to your phone's Gallery (`Movies/RawDrop`).

#### Run from Source (Optional)
```bash
# Windows Desktop
git clone https://github.com/a-les/rawdrop.git
cd rawdrop
pip install -r requirements.txt
python run_desktop.py
```

---

<a name="русский"></a>
## Русский

### Что такое RawDrop?
**RawDrop** — автономное приложение (Windows Desktop + Android) для прямого скачивания видео из TikTok в оригинальном качестве без водяных знаков и пережатия.

### Преимущества
* **Оригинальное качество без водяного знака:** Захват чистого потока напрямую с CDN-серверов в исходном разрешении (вплоть до 1080p).
* **Полная автономность:** Единый `.exe` для Windows и отдельный `.apk` для Android. Не требует подписок, сторонних серверов и плагинов.
* **Быстро и без рекламы:** Без лимитов и сомнительных сайтов-конвертеров.
* **Интеграция «Поделиться» на Android:** Отправка ссылки в RawDrop прямо из меню официального приложения TikTok с автосохранением в системную Галерею (`Movies/RawDrop`).
* **Безопасно:** Работает строго локально на вашем устройстве с проверкой ссылок.

### Установка и запуск

#### 💻 Windows (Десктоп)
1. Скачайте `RawDrop.exe` из раздела [Releases](https://github.com/a-les/rawdrop/releases).
2. Запустите `RawDrop.exe`.
3. Вставьте ссылку на видео и нажмите **Download**. Видео сохранится в папку `downloads/` рядом с программой.

#### 📱 Android (Телефон)
1. Скачайте `RawDrop.apk` из раздела [Releases](https://github.com/a-les/rawdrop/releases) или вкладки Actions.
2. Установите `.apk` на телефон.
3. Скачивайте внутри приложения или нажимайте **«Поделиться» ➔ RawDrop** прямо в TikTok. Ролики сохраняются напрямую в Галерею.

#### Запуск из исходного кода (для разработчиков)
```bash
# Windows
git clone https://github.com/a-les/rawdrop.git
cd rawdrop
pip install -r requirements.txt
python run_desktop.py
```

---

## License & Author
* **License:** [MIT](LICENSE)
* **Author:** [@a-les](https://github.com/a-les)
