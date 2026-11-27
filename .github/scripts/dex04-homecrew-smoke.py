"""Exercise HomeCrew's in-place optimized release update on a clean offline Android emulator."""

import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


APP = "com.hebi.homecrew"
OUT = Path("dex-qa-output")
APK = "app/build/outputs/apk/release/app-release.apk"
HIERARCHY = "/sdcard/dex04-homecrew.xml"


def adb(*arguments, binary=False):
    return subprocess.run(["adb", *arguments], check=True, capture_output=True,
                          text=not binary, timeout=45).stdout


def hierarchy():
    adb("shell", "uiautomator", "dump", HIERARCHY)
    return adb("shell", "cat", HIERARCHY)


def snapshot(name):
    (OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    (OUT / f"{name}.xml").write_text(hierarchy(), encoding="utf-8")


def click_or_find(label, *, click=False, scroll=False):
    for attempt in range(9):
        time.sleep(0.6)
        try:
            nodes = ET.fromstring(hierarchy()).iter("node")
        except (ET.ParseError, subprocess.CalledProcessError):
            continue
        for node in nodes:
            if node.get("package") != APP or label not in (node.get("text"), node.get("content-desc")):
                continue
            bounds = re.findall(r"\d+", node.get("bounds", ""))
            if len(bounds) != 4:
                continue
            x1, y1, x2, y2 = map(int, bounds)
            if x2 <= x1 or y2 <= y1:
                continue
            if click:
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            print(f"{'Tapped' if click else 'Found'}: {label}", flush=True)
            return
        if scroll and attempt >= 2:
            width, height = map(int, re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1])
            adb("shell", "input", "swipe", str(width // 2), str(height * 3 // 4),
                str(width // 2), str(height // 3), "330")
    raise AssertionError(f"Missing HomeCrew UI element: {label}")


def start():
    adb("shell", "am", "force-stop", APP)
    output = adb("shell", "am", "start", "-W", "-n", f"{APP}/.MainActivity")
    assert "Status: ok" in output, output


def run():
    assert any(adb("shell", "getprop", name).strip() == "1"
               for name in ("ro.kernel.qemu", "ro.boot.qemu")), "Use a disposable emulator"
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
    assert adb("shell", "settings", "get", "global", "airplane_mode_on").strip() == "1"
    adb("shell", "svc", "wifi", "disable")
    assert not adb("shell", "pm", "list", "packages", APP).strip()
    adb("install", str(OUT / "baseline.apk"))
    assert "versionCode=15 " in adb("shell", "dumpsys", "package", APP)
    start()
    for label in ("시작하기", "다음", "다음", "선택한 집안일로 시작"):
        click_or_find(label, click=True)
    click_or_find("테마 전환 · 현재 시스템", click=True)
    click_or_find("테마 전환 · 현재 라이트", click=True)
    click_or_find("테마 전환 · 현재 다크")
    snapshot("baseline-dark-setting")
    click_or_find("집안일 탭", click=True)
    click_or_find("화면 제목: 집안일")
    click_or_find("집안일 상세: 설거지", click=True, scroll=True)
    click_or_find("이 회차 완료 처리", click=True, scroll=True)
    click_or_find("이 회차 완료 취소", scroll=True)
    snapshot("baseline-chore-completed")
    adb("install", "-r", APK)
    assert "versionCode=16 " in adb("shell", "dumpsys", "package", APP)
    start()
    click_or_find("테마 전환 · 현재 다크")
    snapshot("upgraded-dark-setting")
    click_or_find("집안일 탭", click=True)
    click_or_find("집안일 상세: 설거지", click=True, scroll=True)
    click_or_find("이 회차 완료 취소", scroll=True)
    snapshot("offline-completion-restored")
    print("Verified first-install onboarding and persistence of completed chore and dark theme after version 15 -> 16 in-place upgrade")


if __name__ == "__main__":
    try:
        run()
    except Exception:
        try:
            snapshot("failure")
            (OUT / "logcat.txt").write_text(adb("logcat", "-d", "-t", "500"), encoding="utf-8")
        finally:
            raise
