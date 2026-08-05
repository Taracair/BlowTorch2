"""Resolve repo paths and external tools. No machine-specific data here."""

from __future__ import annotations

import os
import re
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path

NAME_RE = re.compile(r"^[a-z][a-z0-9_-]{0,31}$")


@dataclass(frozen=True)
class ToolStatus:
    name: str
    found: bool
    path: str | None
    version: str | None = None


@dataclass(frozen=True)
class DeviceStatus:
    serial: str | None
    model: str | None
    source: str
    error: str | None = None


def find_repo_root(start: Path | None = None) -> Path:
    """BlowTorch repo root (directory that contains scripts/ and docs/)."""
    here = (start or Path(__file__).resolve()).parent
    for candidate in (here, *here.parents):
        if (candidate / "scripts" / "check.sh").is_file() and (
            candidate / "docs"
        ).is_dir():
            return candidate
    raise RuntimeError(
        "Could not find BlowTorch repo root (expected scripts/check.sh)."
    )


def media_dir(repo: Path) -> Path:
    path = repo / "docs" / "media"
    path.mkdir(parents=True, exist_ok=True)
    return path


def augment_path() -> str:
    home = Path.home()
    extra = [
        home / ".local" / "bin",
        home / ".cargo" / "bin",
        home / "Android" / "Sdk" / "platform-tools",
    ]
    parts = [str(p) for p in extra if p.is_dir()]
    parts.append(os.environ.get("PATH", ""))
    return os.pathsep.join(parts)


def which(name: str, path: str | None = None) -> str | None:
    return shutil.which(name, path=path or augment_path())


def tool_version(exe: str, *args: str) -> str | None:
    try:
        out = subprocess.run(
            [exe, *args],
            capture_output=True,
            text=True,
            timeout=5,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    text = (out.stdout or out.stderr or "").strip().splitlines()
    return text[0][:80] if text else None


def check_tools() -> list[ToolStatus]:
    names = [
        ("adb", ["version"]),
        ("scrcpy", ["--version"]),
        ("ffmpeg", ["-version"]),
        ("gifski", ["--version"]),
    ]
    path = augment_path()
    result: list[ToolStatus] = []
    for name, version_args in names:
        exe = which(name, path)
        if not exe:
            result.append(ToolStatus(name, False, None))
            continue
        ver = tool_version(exe, *version_args)
        result.append(ToolStatus(name, True, exe, ver))
    return result


def resolve_adb(repo: Path) -> tuple[str | None, str]:
    """Return (adb executable, how it was found)."""
    helper = repo / "scripts" / "adb-device.sh"
    if helper.is_file():
        return which("adb"), "adb-device.sh helper (local)"
    return which("adb"), "PATH"


def resolve_device_serial(repo: Path) -> DeviceStatus:
    adb, source = resolve_adb(repo)
    if not adb:
        return DeviceStatus(None, None, source, "adb not found on PATH")

    helper = repo / "scripts" / "adb-device.sh"
    serial: str | None = None
    err: str | None = None

    if helper.is_file():
        try:
            proc = subprocess.run(
                [str(helper)],
                capture_output=True,
                text=True,
                timeout=120,
                check=False,
                env={**os.environ, "PATH": augment_path()},
            )
            if proc.returncode == 0 and proc.stdout.strip():
                serial = proc.stdout.strip().splitlines()[-1]
            else:
                err = (proc.stderr or proc.stdout or "device helper failed").strip()
                err = err.splitlines()[-1][:120] if err else "no device"
        except (OSError, subprocess.TimeoutExpired) as exc:
            err = str(exc)
    else:
        try:
            proc = subprocess.run(
                [adb, "devices"],
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            for line in proc.stdout.splitlines()[1:]:
                parts = line.split()
                if len(parts) >= 2 and parts[1] == "device":
                    serial = parts[0]
                    break
            if not serial:
                err = "no ready device (scripts/adb-device.sh not installed locally)"
        except (OSError, subprocess.TimeoutExpired) as exc:
            err = str(exc)

    model = None
    if serial:
        try:
            proc = subprocess.run(
                [adb, "-s", serial, "shell", "getprop", "ro.product.model"],
                capture_output=True,
                text=True,
                timeout=5,
                check=False,
            )
            model = (proc.stdout or "").strip() or None
        except (OSError, subprocess.TimeoutExpired):
            model = None

    return DeviceStatus(serial, model, source, err if not serial else None)


def validate_clip_name(name: str) -> str | None:
    name = name.strip().lower()
    if not NAME_RE.match(name):
        return (
            "Use lowercase letters, digits, hyphen, underscore; "
            "must start with a letter (max 32)."
        )
    return None


def markdown_snippet(name: str) -> str:
    return f"![{name}](docs/media/{name}.gif)"


def human_size(num: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if num < 1024:
            return f"{num:.1f} {unit}" if unit != "B" else f"{num} B"
        num /= 1024
    return f"{num:.1f} TB"
