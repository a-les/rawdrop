import sys
import time
import threading
import uvicorn
import webview
from server import app

def start_server():
    """Starts FastAPI server in background thread using direct app instance"""
    uvicorn.run(app, host="127.0.0.1", port=8000, log_level="warning")

def main():
    server_thread = threading.Thread(target=start_server, daemon=True)
    server_thread.start()
    time.sleep(1.0)

    # Создаем нативное окно с зафиксированными размерами (без изменения размера)
    window = webview.create_window(
        title="RawDrop • Pristine Media Extraction",
        url="http://127.0.0.1:8000",
        width=1080,
        height=720,
        resizable=False,
    )

    webview.start()
    sys.exit(0)

if __name__ == "__main__":
    main()
