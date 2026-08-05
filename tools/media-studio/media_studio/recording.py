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

from media_studio.crop import CropRect
from media_studio.paths import augment_path, media_dir


@dataclass(frozen=True)
class VideoInfo:
    width: int
    height: int
    duration: float


def probe_duration_seconds(raw_path: Path) -> int:
    info = probe_video(raw_path)
    if not info:
        return 8
    return max(1, min(120, int(round(info.duration))))


def probe_video(path: Path) -> VideoInfo | None:
    ffmpeg = shutil.which("ffmpeg", path=augment_path())
    if not ffmpeg or not path.is_file():
        return None
    proc = subprocess.run(
        [ffmpeg, "-hide_banner", "-i", str(path)],
        capture_output=True,
        text=True,
        check=False,
    )
    err = proc.stderr
    dur_m = re.search(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)", err)
    # Stream #0:0: Video: h264, …, 480x1080, …
    dim_m = re.search(r"Video:.*?\s(\d{2,5})x(\d{2,5})\b", err)
    if not dur_m or not dim_m:
        return None
    hours, minutes, seconds = dur_m.groups()
    duration = int(hours) * 3600 + int(minutes) * 60 + float(seconds)
    return VideoInfo(int(dim_m.group(1)), int(dim_m.group(2)), duration)


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
        if name.endswith(("-preview.png", "-crop-preview.png")):
            continue
        if name.endswith("-raw.mp4") or ("-raw-" in name and name.endswith(".mp4")):
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


def find_raw_for_clip(repo: Path, clip_name: str) -> Path | None:
    """Newest raw matching clip_name."""
    raws = [
        m
        for m in list_media(repo)
        if m.kind == "raw" and m.clip_name == clip_name
    ]
    return raws[0].path if raws else None


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


def _vf_chain(
    *,
    crop: CropRect | None,
    fps: int | None,
    scale_w: int | None,
) -> str:
    parts: list[str] = []
    if crop is not None:
        parts.append(crop.as_ffmpeg())
    if fps is not None:
        parts.append(f"fps={fps}")
    if scale_w is not None:
        parts.append(f"scale={scale_w}:-2:flags=lanczos")
    return ",".join(parts) if parts else "null"


def extract_preview_frame(
    source: Path,
    dest: Path,
    *,
    at_seconds: float = 0.5,
    crop: CropRect | None = None,
    draw_box: CropRect | None = None,
) -> str | None:
    """Write a PNG preview. Returns error message or None on success."""
    ffmpeg = shutil.which("ffmpeg", path=augment_path())
    if not ffmpeg:
        return "ffmpeg not found"
    filters: list[str] = []
    if draw_box is not None:
        b = draw_box
        filters.append(
            f"drawbox=x={b.x}:y={b.y}:w={b.w}:h={b.h}:color=red@0.8:t=4"
        )
    if crop is not None:
        filters.append(crop.as_ffmpeg())
    vf = ",".join(filters) if filters else "null"
    proc = subprocess.run(
        [
            ffmpeg,
            "-y",
            "-hide_banner",
            "-loglevel",
            "error",
            "-ss",
            str(at_seconds),
            "-i",
            str(source),
            "-frames:v",
            "1",
            "-vf",
            vf,
            str(dest),
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    if proc.returncode != 0 or not dest.is_file():
        return proc.stderr.strip() or "preview extract failed"
    return None


def open_image(path: Path) -> bool:
    for cmd in (
        ["xdg-open", str(path)],
        ["imv", str(path)],
        ["feh", str(path)],
        ["eog", str(path)],
    ):
        if shutil.which(cmd[0]):
            subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return True
    return False


def run_encode(
    repo: Path,
    raw_path: Path,
    name: str,
    seconds: int,
    on_line: Callable[[str], None],
    *,
    crop: CropRect | None = None,
    trim_start: float = 0.2,
) -> int:
    """Re-encode raw MP4 → compact MP4 + GIF. Optional crop + trim start."""
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
        if crop:
            on_line(f"Crop: {crop.label()} ({crop.as_ffmpeg()})")
        on_line(f"Trim start: {trim_start}s, duration: {seconds}s")
        on_line(f"Encoding {out_mp4.name} …")
        vf_mp4 = _vf_chain(crop=crop, fps=None, scale_w=720)
        p1 = subprocess.run(
            [
                ffmpeg,
                "-y",
                "-hide_banner",
                "-loglevel",
                "error",
                "-ss",
                str(trim_start),
                "-i",
                str(raw_path),
                "-t",
                str(seconds),
                "-vf",
                vf_mp4,
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
        vf_gif = _vf_chain(crop=crop, fps=12, scale_w=540)
        p2 = subprocess.run(
            [
                ffmpeg,
                "-y",
                "-hide_banner",
                "-loglevel",
                "error",
                "-ss",
                str(trim_start),
                "-i",
                str(raw_path),
                "-t",
                str(seconds),
                "-vf",
                vf_gif,
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
