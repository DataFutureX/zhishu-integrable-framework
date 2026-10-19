@echo off
cd /d "%~dp0.."
pnpm run dev:desktop:fast
if errorlevel 1 pause
