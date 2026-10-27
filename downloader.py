import os
import re
from pathlib import Path
from typing import Callable, Optional
import yt_dlp

class TikTokDownloader:
    def __init__(self, output_dir: Optional[str] = None):
        """
        Инициализирует загрузчик.
        output_dir: Папка для сохранения скачанных видео.
        По умолчанию — папка 'downloads' в директории проекта.
        """
        if output_dir:
            self.output_dir = Path(output_dir)
        else:
            import sys
            if getattr(sys, "frozen", False):
                app_dir = Path(sys.executable).parent
            else:
                app_dir = Path(__file__).parent
            self.output_dir = app_dir / "downloads"
        
        self.output_dir.mkdir(parents=True, exist_ok=True)

    def get_info(self, url: str) -> dict:
        """
        Получает информацию о видео без его скачивания (быстрый запрос).
        Возвращает метаданные: id, название, автора, превью, длительность и т.д.
        """
        ydl_opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url.strip(), download=False)
            if not info:
                raise ValueError("Не удалось получить информацию о видео.")

            return {
                "id": info.get("id"),
                "title": info.get("title") or info.get("description") or "tiktok_video",
                "author": info.get("uploader") or info.get("creator") or "Unknown",
                "author_id": info.get("uploader_id") or "",
                "duration": info.get("duration", 0),
                "cover_url": info.get("thumbnail"),
                "original_url": url.strip(),
            }

    def download(self, url: str, progress_callback: Optional[Callable[[dict], None]] = None) -> str:
        """
        Скачивает видео без водяного знака в наилучшем качестве.
        progress_callback: функция обратного вызова, принимающая словарь с данными о прогрессе:
          - percent: float (0.0 - 100.0)
          - downloaded_bytes: int
          - total_bytes: int
          - speed_str: str
          - eta_str: str
        Возвращает абсолютный путь к сохраненному файлу.
        """
        downloaded_filepath = []

        def ytdlp_hook(d: dict):
            status = d.get("status")
            if status == "downloading":
                total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                downloaded = d.get("downloaded_bytes", 0)
                percent = (downloaded / total * 100) if total > 0 else 0.0
                speed = d.get("_speed_str", "N/A")
                eta = d.get("_eta_str", "N/A")

                if progress_callback:
                    progress_callback({
                        "status": "downloading",
                        "percent": round(percent, 1),
                        "downloaded_bytes": downloaded,
                        "total_bytes": total,
                        "speed_str": speed,
                        "eta_str": eta,
                    })
            elif status == "finished":
                filename = d.get("filename")
                if filename:
                    downloaded_filepath.append(filename)
                if progress_callback:
                    progress_callback({"status": "finished", "percent": 100.0})

        out_template = str(self.output_dir / "%(title).80s [%(id)s].%(ext)s")

        ydl_opts = {
            "outtmpl": out_template,
            "format": "bestvideo+bestaudio/best",
            "progress_hooks": [ytdlp_hook],
            "quiet": True,
            "no_warnings": True,
        }

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url.strip()])

        if downloaded_filepath:
            return os.path.abspath(downloaded_filepath[0])

        # Если имя не перехватилось в хуке, найдем последний созданный файл в папке
        files = list(self.output_dir.glob("*.mp4"))
        if files:
            latest_file = max(files, key=lambda f: f.stat().st_mtime)
            return str(latest_file.resolve())

        raise RuntimeError("Файл скачан, но путь определить не удалось.")

if __name__ == "__main__":
    import sys

    url = sys.argv[1] if len(sys.argv) > 1 else input("Вставь ссылку на видео TikTok: ").strip()
    if not url:
        print("[!] Ссылка не указана.")
        sys.exit(1)

    downloader = TikTokDownloader()
    
    print("\n🔍 Получаем информацию о видео...")
    try:
        info = downloader.get_info(url)
        print(f"🎬 Название: {info['title']}")
        print(f"👤 Автор:    {info['author']}")
        print(f"⏱️ Длительность: {info['duration']} сек.")
        print(f"🖼️ Обложка:  {info['cover_url']}")
        print("\n⬇️ Начинаем скачивание...")

        def cli_progress(data: dict):
            if data["status"] == "downloading":
                p = data["percent"]
                s = data["speed_str"]
                eta = data["eta_str"]
                print(f"\r[Скачивание]: {p:.1f}% | Скорость: {s} | Осталось: {eta}", end="", flush=True)
            elif data["status"] == "finished":
                print("\n[✔] Загрузка завершена! Обработка файла...")

        saved_path = downloader.download(url, progress_callback=cli_progress)
        print(f"\n🎉 Видео успешно сохранено:\n{saved_path}")

    except Exception as e:
        print(f"\n[❌] Ошибка: {e}")
