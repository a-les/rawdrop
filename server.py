import os
import asyncio
import json
import time
from pathlib import Path
from urllib.parse import urlparse
from fastapi import FastAPI, HTTPException, Query
from fastapi.staticfiles import StaticFiles
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from downloader import TikTokDownloader

app = FastAPI(title="RawDrop API")

import sys

# Directories
if getattr(sys, "frozen", False):
    BASE_DIR = Path(sys._MEIPASS)
else:
    BASE_DIR = Path(__file__).parent
STATIC_DIR = BASE_DIR / "static"
STATIC_DIR.mkdir(exist_ok=True)

downloader = TikTokDownloader()

ALLOWED_TIKTOK_DOMAINS = {
    "tiktok.com",
    "www.tiktok.com",
    "m.tiktok.com",
    "vm.tiktok.com",
    "vt.tiktok.com",
    "t.tiktok.com",
}

def validate_tiktok_url(url: str) -> str:
    """
    Strict URL validator preventing SSRF, protocol injection,
    and restricting operations exclusively to verified TikTok endpoints.
    """
    clean_url = (url or "").strip()
    if not clean_url:
        raise HTTPException(status_code=400, detail="URL cannot be empty")
    
    # Prepend https if user pasted without protocol
    if not clean_url.startswith(("http://", "https://")):
        clean_url = "https://" + clean_url

    try:
        parsed = urlparse(clean_url)
    except Exception:
        raise HTTPException(status_code=400, detail="Malformed URL structure")

    if parsed.scheme not in ("http", "https"):
        raise HTTPException(status_code=400, detail="Only HTTP/HTTPS protocols are permitted")

    host = (parsed.hostname or "").lower()
    if not host:
        raise HTTPException(status_code=400, detail="Invalid host in URL")

    # Guard: only allow official TikTok domains or subdomains
    is_valid = (host in ALLOWED_TIKTOK_DOMAINS) or host.endswith(".tiktok.com")
    if not is_valid:
        raise HTTPException(
            status_code=400,
            detail="Security constraint: Only official TikTok links (tiktok.com, vt.tiktok.com, vm.tiktok.com) are allowed."
        )

    return clean_url

class InfoRequest(BaseModel):
    url: str

class OpenFileRequest(BaseModel):
    path: str

@app.post("/api/info")
async def get_info(req: InfoRequest):
    """
    Returns verified video metadata with domain whitelist validation.
    """
    verified_url = validate_tiktok_url(req.url)
    
    try:
        info = await asyncio.to_thread(downloader.get_info, verified_url)
        return {"status": "ok", "data": info}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.get("/api/download-stream")
async def download_stream(url: str = Query(...)):
    """
    Server-Sent Events (SSE) stream for download progress with validated URL.
    """
    verified_url = validate_tiktok_url(url)
    queue = asyncio.Queue()
    loop = asyncio.get_running_loop()

    def progress_callback(data: dict):
        asyncio.run_coroutine_threadsafe(queue.put(data), loop)

    def run_download():
        try:
            filepath = downloader.download(verified_url, progress_callback=progress_callback)
            asyncio.run_coroutine_threadsafe(
                queue.put({"status": "done", "filepath": filepath}), loop
            )
        except Exception as e:
            asyncio.run_coroutine_threadsafe(
                queue.put({"status": "error", "message": str(e)}), loop
            )

    loop.run_in_executor(None, run_download)

    async def event_generator():
        while True:
            data = await queue.get()
            yield f"data: {json.dumps(data, ensure_ascii=False)}\n\n"
            if data.get("status") in ("done", "error"):
                break

    return StreamingResponse(event_generator(), media_type="text/event-stream")

@app.get("/api/history")
async def get_history():
    """
    Returns list of recently downloaded video files in downloads folder.
    """
    try:
        files = list(downloader.output_dir.glob("*.mp4"))
        files.sort(key=lambda f: f.stat().st_mtime, reverse=True)
        
        items = []
        for f in files[:8]:
            stat = f.stat()
            size_mb = round(stat.st_size / (1024 * 1024), 1)
            created_ago = int(time.time() - stat.st_mtime)
            
            if created_ago < 60:
                time_str = "Just now"
            elif created_ago < 3600:
                time_str = f"{created_ago // 60}m ago"
            elif created_ago < 86400:
                time_str = f"{created_ago // 3600}h ago"
            else:
                time_str = f"{created_ago // 86400}d ago"

            clean_title = f.stem.split(" [")[0]
            items.append({
                "filename": f.name,
                "title": clean_title,
                "path": str(f.resolve()),
                "size_mb": size_mb,
                "time_ago": time_str
            })

        return {"status": "ok", "items": items}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/open-folder")
async def open_folder():
    """
    Safely opens the isolated downloads folder in Windows Explorer.
    """
    try:
        downloads_dir = downloader.output_dir.resolve()
        downloads_dir.mkdir(parents=True, exist_ok=True)
        os.startfile(str(downloads_dir))
        return {"status": "ok", "path": str(downloads_dir)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/open-file")
async def open_file(req: OpenFileRequest):
    """
    Safely opens/plays a specific downloaded file.
    Guaranteed Path Traversal and Arbitrary Code Execution protection:
    - Target must reside strictly inside downloads/
    - File extension must be strictly media (.mp4, .mov, .m4a)
    - File must exist as a regular file
    """
    try:
        raw_path = req.path.strip()
        if not raw_path:
            raise HTTPException(status_code=400, detail="File path cannot be empty")

        target_file = Path(raw_path).resolve()
        downloads_dir = downloader.output_dir.resolve()

        # Path Traversal Guard: target must reside within downloads_dir
        if not target_file.is_relative_to(downloads_dir):
            raise HTTPException(
                status_code=403, 
                detail="Security violation: Access outside the downloads directory is forbidden."
            )

        # File type restriction: strictly media files
        if target_file.suffix.lower() not in (".mp4", ".mov", ".m4a"):
            raise HTTPException(
                status_code=403,
                detail="Security violation: Only verified media files (.mp4) can be opened."
            )

        if not target_file.is_file() or not target_file.exists():
            raise HTTPException(status_code=404, detail="Requested media file not found on disk.")

        os.startfile(str(target_file))
        return {"status": "ok"}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# Static assets
app.mount("/", StaticFiles(directory=str(STATIC_DIR), html=True), name="static")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("server:app", host="127.0.0.1", port=8000, reload=True)
