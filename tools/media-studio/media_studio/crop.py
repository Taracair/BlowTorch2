"""Crop presets and geometry helpers for demo re-encodes."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass


@dataclass(frozen=True)
class CropRect:
    """ffmpeg crop=w:h:x:y — origin is top-left of the source frame."""

    x: int
    y: int
    w: int
    h: int

    def as_ffmpeg(self) -> str:
        return f"crop={self.w}:{self.h}:{self.x}:{self.y}"

    def label(self) -> str:
        return f"{self.w}x{self.h}+{self.x}+{self.y}"


def _even(n: int) -> int:
    """H.264 prefers even dimensions."""
    return n if n % 2 == 0 else max(2, n - 1)


def make_rect(x: int, y: int, w: int, h: int, src_w: int, src_h: int) -> CropRect | str:
    """Clamp and validate a crop against the source size. Returns error string or CropRect."""
    if src_w < 2 or src_h < 2:
        return "Source frame too small."
    x = max(0, min(int(x), src_w - 2))
    y = max(0, min(int(y), src_h - 2))
    w = _even(max(2, min(int(w), src_w - x)))
    h = _even(max(2, min(int(h), src_h - y)))
    if x + w > src_w or y + h > src_h:
        return f"Crop exceeds frame ({src_w}x{src_h})."
    return CropRect(x, y, w, h)


def _must(rect: CropRect | str) -> CropRect:
    if isinstance(rect, str):
        raise ValueError(rect)
    return rect


PresetFn = Callable[[int, int], CropRect]


def _full(sw: int, sh: int) -> CropRect:
    return CropRect(0, 0, _even(sw), _even(sh))


def _hide_status(sw: int, sh: int) -> CropRect:
    top = max(24, int(round(sh * 0.045)))
    return _must(make_rect(0, top, sw, sh - top, sw, sh))


def _bottom_half(sw: int, sh: int) -> CropRect:
    y = sh // 2
    return _must(make_rect(0, y, sw, sh - y, sw, sh))


def _bottom_60(sw: int, sh: int) -> CropRect:
    y = int(round(sh * 0.40))
    return _must(make_rect(0, y, sw, sh - y, sw, sh))


def _top_half(sw: int, sh: int) -> CropRect:
    return _must(make_rect(0, 0, sw, sh // 2, sw, sh))


def _center_70(sw: int, sh: int) -> CropRect:
    w = int(round(sw * 0.70))
    h = int(round(sh * 0.70))
    x = (sw - w) // 2
    y = (sh - h) // 2
    return _must(make_rect(x, y, w, h, sw, sh))


def _inset_10(sw: int, sh: int) -> CropRect:
    m_x = int(round(sw * 0.10))
    m_y = int(round(sh * 0.10))
    return _must(make_rect(m_x, m_y, sw - 2 * m_x, sh - 2 * m_y, sw, sh))


# (id, UI label, function)
CROP_PRESETS: list[tuple[str, str, PresetFn]] = [
    ("full", "Full frame (no crop)", _full),
    ("hide_status", "Hide status bar (~top 4.5%)", _hide_status),
    ("bottom_60", "Bottom 60% (button pads)", _bottom_60),
    ("bottom_half", "Bottom half", _bottom_half),
    ("top_half", "Top half (mud window)", _top_half),
    ("center_70", "Center 70%", _center_70),
    ("inset_10", "Inset 10% margins", _inset_10),
    ("custom", "Custom X / Y / W / H", _full),
]


def apply_preset(preset_id: str, src_w: int, src_h: int) -> CropRect | str:
    for pid, _label, fn in CROP_PRESETS:
        if pid == preset_id:
            try:
                return fn(src_w, src_h)
            except ValueError as exc:
                return str(exc)
    return f"Unknown preset: {preset_id}"


def select_options() -> list[tuple[str, str]]:
    """(label, value) pairs for Textual Select."""
    return [(label, pid) for pid, label, _fn in CROP_PRESETS]
