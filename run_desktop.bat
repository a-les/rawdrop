@echo off
title TikTok Downloader - Desktop
cd /d "%~dp0"
call "venv\Scripts\activate.bat"
python run_desktop.py
