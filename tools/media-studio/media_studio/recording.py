"""Record and transcode helpers."""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import tempfile
from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from media_studio.paths import augment_path, media_dir


def probe_duration_seconds(raw_path: Path) -> int:
    ffmpeg = shutil.which("ffmpeg", path=augment_path())
    if not ffmpeg:
        return 8
    proc = subprocess.run(
        [ffmpeg, "-hide_banner", "-i", str(raw_path)],
        capture_output=True,
        text=True,
        check=False,
    )
    match = re.search(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)", proc.stderr)
    if not match:
        return 8
    hours, minutes, seconds = match.groups()
    total = int(hours) * 3600 + int(minutes) * 60 + float(seconds)
    return max(1, min(120, int(round(total))))


@dataclass(frozen=True)
class MediaFile:
    path: Path
    kind: str  # raw | mp4 | gif
    clip_name: str
    size: int
    mtime: float

    @property
    def mtime_label(self) -> str:
        return datetime.fromtimestamp(self.mtime).strftime("%Y-%m-%d %H:%M")


def list_media(repo: Path) -> list[MediaFile]:
    root = media_dir(repo)
    items: list[MediaFile] = []
    for path in sorted(root.iterdir(), key=lambda p: p.stat().st_mtime, reverse=True):
        if not path.is_file():
            continue
        name = path.name
        if name.endswith("-raw.mp4") or "-raw-" in name and name.endswith(".mp4"):
            kind = "raw"
            clip = name.split("-raw")[0]
        elif name.endswith(".mp4"):
            kind = "mp4"
            clip = path.stem
        elif name.endswith(".gif"):
            kind = "gif"
            clip = path.stem
        else:
            continue
        st = path.stat()
        items.append(MediaFile(path, kind, clip, st.st_size, st.st_mtime))
    return items


def run_record(
    repo: Path,
    name: str,
    seconds: int,
    mirror: bool,
    on_line: Callable[[str], None],
) -> int:
    script = repo / "scripts" / "demo-record.sh"
    if not script.is_file():
        on_line("ERROR: scripts/demo-record.sh not found")
        return 1

    args = [str(script), name, str(seconds)]
    if mirror:
        args.append("--window")

    on_line(f"$ {' '.join(args)}")
    proc = subprocess.Popen(
        args,
        cwd=str(repo),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        env={**os.environ, "PATH": augment_path()},
    )
    assert proc.stdout is not None
    for line in proc.stdout:
        on_line(line.rstrip())
    return proc.wait()


def run_encode(
    repo: Path,
    raw_path: Path,
    name: str,
    seconds: int,
    on_line: Callable[[str], None],
) -> int:
    """Re-encode an existing raw MP4 to compact MP4 + GIF."""
    gifski = shutil.which("gifski", path=augment_path())
    ffmpeg = shutil.which("ffmpeg", path=augment_path())
    if not gifski or not ffmpeg:
        on_line("ERROR: ffmpeg and gifski must be on PATH")
        return 1
    if not raw_path.is_file():
        on_line(f"ERROR: missing {raw_path}")
        return 1

    out_mp4 = media_dir(repo) / f"{name}.mp4"
    out_gif = media_dir(repo) / f"{name}.gif"
    frames = Path(tempfile.mkdtemp(prefix="bt-demo-frames."))

    try:
        on_line(f"Encoding {out_mp4.name} …")
        p1 = subprocess.run(
            [
                ffmpeg,
                "-y",
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                str(raw_path),
                "-ss",
                "0.2",
                "-t",
                str(seconds),
                "-vf",
                "scale=720:-2:flags=lanczos",
                "-c:v",
                "libx264",
                "-crf",
                "23",
                "-preset",
                "medium",
                "-an",
                "-movflags",
                "+faststart",
                str(out_mp4),
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        if p1.returncode != 0:
            on_line(p1.stderr or "ffmpeg failed")
            return p1.returncode

        on_line(f"Building {out_gif.name} …")
        p2 = subprocess.run(
            [
                ffmpeg,
                "-y",
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                str(raw_path),
                "-ss",
                "0.2",
                "-t",
                str(seconds),
                "-vf",
                "fps=12,scale=540:-1:flags=lanczos",
                f"{frames}/%04d.png",
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        if p2.returncode != 0:
            on_line(p2.stderr or "frame extract failed")
            return p2.returncode

        pngs = sorted(frames.glob("*.png"))
        if not pngs:
            on_line("ERROR: no frames extracted")
            return 1

        p3 = subprocess.run(
            [gifski, "-o", str(out_gif), "--width", "540", "--fps", "12", *map(str, pngs)],
            capture_output=True,
            text=True,
            check=False,
        )
        if p3.returncode != 0:
            on_line(p3.stderr or p3.stdout or "gifski failed")
            return p3.returncode

        on_line(f"Done: {out_mp4} ({out_mp4.stat().st_size} bytes)")
        on_line(f"Done: {out_gif} ({out_gif.stat().st_size} bytes)")
        return 0
    finally:
        for child in frames.glob("*"):
            child.unlink(missing_ok=True)
        frames.rmdir()
