"""Textual TUI for BlowTorch demo recordings."""

from __future__ import annotations

import argparse
import shutil
import subprocess
from pathlib import Path

from textual import on, work
from textual.app import App, ComposeResult
from textual.binding import Binding
from textual.containers import Horizontal, Vertical, VerticalScroll
from textual.screen import Screen
from textual.widgets import (
    Button,
    Checkbox,
    DataTable,
    Footer,
    Header,
    Input,
    Label,
    RichLog,
    Select,
    Static,
)

from media_studio import __version__
from media_studio.crop import CropRect, apply_preset, make_rect, select_options
from media_studio.paths import (
    check_tools,
    find_repo_root,
    human_size,
    markdown_snippet,
    media_dir,
    resolve_device_serial,
    validate_clip_name,
)
from media_studio.recording import (
    MediaFile,
    extract_preview_frame,
    find_raw_for_clip,
    list_media,
    open_image,
    probe_duration_seconds,
    probe_video,
    run_encode,
    run_record,
)


class StatusScreen(Screen):
    """Tools, device, and quick navigation."""

    BINDINGS = [
        Binding("r", "open_record", "Record", show=True),
        Binding("l", "open_library", "Library", show=True),
        Binding("f5", "refresh", "Refresh", show=True),
    ]

    def compose(self) -> ComposeResult:
        yield Header()
        with VerticalScroll(id="status-scroll"):
            yield Static(id="status-body")
            yield Label("Quick start", classes="section-title")
            yield Static(
                "1. Open BlowTorch on the phone (big font, Do Not Disturb on).\n"
                "2. Press [b]r[/b]ecord, set a clip name, hit Start.\n"
                "3. Perform the demo on the phone during the countdown.\n"
                "4. In [b]l[/b]ibrary: crop the interesting region, then copy markdown.",
                id="quickstart",
            )
            with Horizontal(classes="button-row"):
                yield Button("Record…", id="btn-record", variant="primary")
                yield Button("Library…", id="btn-library")
                yield Button("Refresh", id="btn-refresh")
        yield Footer()

    def on_mount(self) -> None:
        self.refresh_status()

    def action_refresh(self) -> None:
        self.refresh_status()

    def action_open_record(self) -> None:
        self.app.push_screen("record")

    def action_open_library(self) -> None:
        self.app.push_screen("library")

    @on(Button.Pressed, "#btn-record")
    def _btn_record(self) -> None:
        self.action_open_record()

    @on(Button.Pressed, "#btn-library")
    def _btn_library(self) -> None:
        self.action_open_library()

    @on(Button.Pressed, "#btn-refresh")
    def _btn_refresh(self) -> None:
        self.action_refresh()

    def refresh_status(self) -> None:
        repo: Path = self.app.repo  # type: ignore[attr-defined]
        tools = check_tools()
        device = resolve_device_serial(repo)
        lines = [
            f"[bold]Media Studio[/bold] v{__version__}",
            f"Repo: {repo}",
            f"Output: {media_dir(repo)}",
            "",
            "[bold]Tools[/bold]",
        ]
        for t in tools:
            mark = "[green]OK[/green]" if t.found else "[red]MISSING[/red]"
            detail = t.path or "not found"
            if t.version:
                detail = f"{detail} — {t.version}"
            lines.append(f"  {mark} {t.name}: {detail}")

        lines.extend(["", "[bold]Device[/bold]"])
        if device.serial:
            model = f" ({device.model})" if device.model else ""
            lines.append(f"  [green]Ready[/green] {device.serial}{model}")
            lines.append(f"  via {device.source}")
        else:
            lines.append(f"  [red]Not ready[/red] {device.error or ''}")
            lines.append(
                "  Tip: copy scripts/adb-device.sh locally or connect USB/wifi adb."
            )

        missing = [t.name for t in tools if not t.found]
        if missing:
            lines.extend(
                [
                    "",
                    "[yellow]Install hints[/yellow]",
                    "  scrcpy: ~/.local/bin or pacman -S scrcpy",
                    "  gifski: cargo install gifski",
                    "  ffmpeg: pacman -S ffmpeg",
                ]
            )

        self.query_one("#status-body", Static).update("\n".join(lines))


class RecordScreen(Screen):
    """Configure and run a new recording."""

    BINDINGS = [
        Binding("escape", "pop_screen", "Back", show=True),
        Binding("ctrl+s", "start", "Start", show=True),
    ]

    def compose(self) -> ComposeResult:
        yield Header()
        with Vertical(id="record-form"):
            yield Label("Clip name (files: docs/media/NAME.gif)")
            yield Input(placeholder="buttons", id="name-input")
            yield Label("Duration (seconds)")
            yield Input(value="8", id="seconds-input")
            yield Checkbox("Mirror on PC while recording (--window)", id="mirror")
            yield Static(
                "Record the full phone screen. Crop the interesting region afterward "
                "in Library → Crop.",
                classes="hint",
            )
            with Horizontal(classes="button-row"):
                yield Button("Start recording", id="btn-start", variant="success")
                yield Button("Back", id="btn-back")
            yield Label("Log", classes="section-title")
            yield RichLog(id="record-log", highlight=True, markup=True)
        yield Footer()

    def action_pop_screen(self) -> None:
        self.app.pop_screen()

    def action_start(self) -> None:
        self._start_recording()

    @on(Button.Pressed, "#btn-back")
    def _back(self) -> None:
        self.action_pop_screen()

    @on(Button.Pressed, "#btn-start")
    def _start(self) -> None:
        self._start_recording()

    def _start_recording(self) -> None:
        name = self.query_one("#name-input", Input).value.strip().lower()
        err = validate_clip_name(name)
        if err:
            self._log(f"[red]{err}[/red]")
            return
        try:
            seconds = int(self.query_one("#seconds-input", Input).value.strip())
        except ValueError:
            self._log("[red]Duration must be an integer.[/red]")
            return
        if seconds < 1 or seconds > 120:
            self._log("[red]Duration must be between 1 and 120 seconds.[/red]")
            return

        mirror = self.query_one("#mirror", Checkbox).value
        self.query_one("#btn-start", Button).disabled = True
        self._run_record(name, seconds, mirror)

    def _log(self, line: str) -> None:
        self.query_one("#record-log", RichLog).write(line)

    @work(thread=True)
    def _run_record(self, name: str, seconds: int, mirror: bool) -> None:
        repo: Path = self.app.repo  # type: ignore[attr-defined]
        log = self.query_one("#record-log", RichLog)

        def on_line(text: str) -> None:
            self.app.call_from_thread(log.write, text)

        self.app.call_from_thread(
            log.write,
            f"[bold]Recording {name} for {seconds}s…[/bold] "
            f"({'mirror' if mirror else 'headless'})",
        )
        code = run_record(repo, name, seconds, mirror, on_line)
        if code == 0:
            self.app.call_from_thread(
                log.write,
                f"\n[green]Done.[/green] Next: Library → select raw → Crop.\n"
                f"  README: {markdown_snippet(name)}",
            )
        else:
            self.app.call_from_thread(log.write, f"\n[red]Failed (exit {code}).[/red]")
        self.app.call_from_thread(self._set_start_enabled, True)

    def _set_start_enabled(self, enabled: bool) -> None:
        self.query_one("#btn-start", Button).disabled = not enabled


class CropScreen(Screen):
    """Crop + trim a raw recording, then re-encode MP4/GIF."""

    BINDINGS = [
        Binding("escape", "pop_screen", "Back", show=True),
        Binding("ctrl+s", "encode", "Encode", show=True),
        Binding("p", "preview_box", "Preview box", show=True),
        Binding("c", "preview_crop", "Preview crop", show=True),
    ]

    def __init__(self, raw_path: Path, clip_name: str) -> None:
        super().__init__()
        self.raw_path = raw_path
        self.clip_name = clip_name
        self.src_w = 0
        self.src_h = 0
        self.src_duration = 0.0

    def compose(self) -> ComposeResult:
        yield Header()
        with VerticalScroll(id="crop-form"):
            yield Static(id="crop-info")
            yield Label("Preset")
            yield Select(
                select_options(),
                value="bottom_60",
                id="preset",
                allow_blank=False,
            )
            yield Label("Custom crop (pixels from top-left of the raw frame)")
            with Horizontal(classes="xywh-row"):
                yield Input(placeholder="X", id="crop-x", classes="coord")
                yield Input(placeholder="Y", id="crop-y", classes="coord")
                yield Input(placeholder="W", id="crop-w", classes="coord")
                yield Input(placeholder="H", id="crop-h", classes="coord")
            yield Label("Trim start (seconds) / Duration (seconds)")
            with Horizontal(classes="xywh-row"):
                yield Input(value="0.2", id="trim-start", classes="coord")
                yield Input(value="8", id="trim-duration", classes="coord")
            yield Label("Output clip name (overwrites NAME.mp4 / NAME.gif)")
            yield Input(value=self.clip_name, id="out-name")
            yield Static(
                "Tips: Open [b]full frame[/b] to measure X/Y/W/H. "
                "[b]Preview box[/b] draws a red rectangle on the full frame. "
                "[b]Preview crop[/b] shows only the cut region. Raw file stays untouched.",
                classes="hint",
            )
            with Horizontal(classes="button-row"):
                yield Button("Open full frame", id="btn-full")
                yield Button("Preview box", id="btn-box", variant="primary")
                yield Button("Preview crop", id="btn-crop-prev")
            with Horizontal(classes="button-row"):
                yield Button("Encode MP4+GIF", id="btn-encode", variant="success")
                yield Button("Back", id="btn-back")
            yield RichLog(id="crop-log", highlight=True, markup=True)
        yield Footer()

    def on_mount(self) -> None:
        info = probe_video(self.raw_path)
        if not info:
            self.query_one("#crop-info", Static).update(
                f"[red]Could not probe[/red] {self.raw_path.name}"
            )
            return
        self.src_w, self.src_h = info.width, info.height
        self.src_duration = info.duration
        self.query_one("#crop-info", Static).update(
            f"[bold]Source[/bold] {self.raw_path.name} — "
            f"{info.width}×{info.height}px, {info.duration:.1f}s"
        )
        self.query_one("#trim-duration", Input).value = str(
            max(1, min(120, int(round(info.duration))))
        )
        self._apply_preset_to_inputs("bottom_60")

    def action_pop_screen(self) -> None:
        self.app.pop_screen()

    def action_encode(self) -> None:
        self._encode()

    def action_preview_box(self) -> None:
        self._preview(mode="box")

    def action_preview_crop(self) -> None:
        self._preview(mode="crop")

    @on(Button.Pressed, "#btn-back")
    def _back(self) -> None:
        self.action_pop_screen()

    @on(Button.Pressed, "#btn-full")
    def _full(self) -> None:
        self._preview(mode="full")

    @on(Button.Pressed, "#btn-box")
    def _box(self) -> None:
        self._preview(mode="box")

    @on(Button.Pressed, "#btn-crop-prev")
    def _crop_prev(self) -> None:
        self._preview(mode="crop")

    @on(Button.Pressed, "#btn-encode")
    def _encode_btn(self) -> None:
        self._encode()

    @on(Select.Changed, "#preset")
    def _preset_changed(self, event: Select.Changed) -> None:
        if event.value is Select.BLANK:
            return
        preset_id = str(event.value)
        if preset_id != "custom":
            self._apply_preset_to_inputs(preset_id)

    def _log(self, line: str) -> None:
        self.query_one("#crop-log", RichLog).write(line)

    def _apply_preset_to_inputs(self, preset_id: str) -> None:
        if self.src_w <= 0:
            return
        rect = apply_preset(preset_id, self.src_w, self.src_h)
        if isinstance(rect, str):
            self._log(f"[red]{rect}[/red]")
            return
        self.query_one("#crop-x", Input).value = str(rect.x)
        self.query_one("#crop-y", Input).value = str(rect.y)
        self.query_one("#crop-w", Input).value = str(rect.w)
        self.query_one("#crop-h", Input).value = str(rect.h)

    def _read_crop(self) -> CropRect | str:
        try:
            x = int(self.query_one("#crop-x", Input).value.strip())
            y = int(self.query_one("#crop-y", Input).value.strip())
            w = int(self.query_one("#crop-w", Input).value.strip())
            h = int(self.query_one("#crop-h", Input).value.strip())
        except ValueError:
            return "X/Y/W/H must be integers."
        if self.src_w <= 0:
            return "Source not probed."
        return make_rect(x, y, w, h, self.src_w, self.src_h)

    def _read_trim(self) -> tuple[float, int] | str:
        try:
            start = float(self.query_one("#trim-start", Input).value.strip())
            duration = int(float(self.query_one("#trim-duration", Input).value.strip()))
        except ValueError:
            return "Trim start / duration must be numbers."
        if start < 0 or start > 600:
            return "Trim start out of range."
        if duration < 1 or duration > 120:
            return "Duration must be 1–120 seconds."
        return start, duration

    def _preview(self, mode: str) -> None:
        repo: Path = self.app.repo  # type: ignore[attr-defined]
        dest_dir = media_dir(repo)
        if mode == "full":
            dest = dest_dir / f"{self.clip_name}-preview.png"
            err = extract_preview_frame(self.raw_path, dest, at_seconds=0.5)
        else:
            crop = self._read_crop()
            if isinstance(crop, str):
                self._log(f"[red]{crop}[/red]")
                return
            if mode == "box":
                dest = dest_dir / f"{self.clip_name}-preview.png"
                err = extract_preview_frame(
                    self.raw_path, dest, at_seconds=0.5, draw_box=crop
                )
            else:
                dest = dest_dir / f"{self.clip_name}-crop-preview.png"
                err = extract_preview_frame(
                    self.raw_path, dest, at_seconds=0.5, crop=crop
                )
        if err:
            self._log(f"[red]{err}[/red]")
            return
        opened = open_image(dest)
        self._log(
            f"[green]Wrote[/green] {dest.name}"
            + (" — opened in viewer" if opened else f" — open: {dest}")
        )

    def _encode(self) -> None:
        crop = self._read_crop()
        if isinstance(crop, str):
            self._log(f"[red]{crop}[/red]")
            return
        trim = self._read_trim()
        if isinstance(trim, str):
            self._log(f"[red]{trim}[/red]")
            return
        trim_start, duration = trim
        out_name = self.query_one("#out-name", Input).value.strip().lower()
        err = validate_clip_name(out_name)
        if err:
            self._log(f"[red]{err}[/red]")
            return
        # Skip crop filter when it is the full frame
        use_crop: CropRect | None = crop
        if (
            crop.x == 0
            and crop.y == 0
            and crop.w >= self.src_w - 1
            and crop.h >= self.src_h - 1
        ):
            use_crop = None
        self.query_one("#btn-encode", Button).disabled = True
        self._run_encode(out_name, duration, trim_start, use_crop)

    @work(thread=True)
    def _run_encode(
        self,
        out_name: str,
        duration: int,
        trim_start: float,
        crop: CropRect | None,
    ) -> None:
        repo: Path = self.app.repo  # type: ignore[attr-defined]
        log = self.query_one("#crop-log", RichLog)

        def on_line(text: str) -> None:
            self.app.call_from_thread(log.write, text)

        code = run_encode(
            repo,
            self.raw_path,
            out_name,
            duration,
            on_line,
            crop=crop,
            trim_start=trim_start,
        )
        if code == 0:
            self.app.call_from_thread(
                log.write,
                f"\n[green]Done.[/green] {markdown_snippet(out_name)}",
            )
        else:
            self.app.call_from_thread(log.write, f"\n[red]Encode failed ({code}).[/red]")
        self.app.call_from_thread(self._set_encode_enabled, True)

    def _set_encode_enabled(self, enabled: bool) -> None:
        self.query_one("#btn-encode", Button).disabled = not enabled


class LibraryScreen(Screen):
    """Browse, copy markdown, crop, re-encode, delete."""

    BINDINGS = [
        Binding("escape", "pop_screen", "Back", show=True),
        Binding("f5", "reload", "Reload", show=True),
        Binding("c", "copy_markdown", "Copy MD", show=True),
        Binding("x", "open_crop", "Crop", show=True),
        Binding("d", "delete_file", "Delete", show=True),
        Binding("e", "reencode", "Re-encode", show=True),
        Binding("o", "open_folder", "Open dir", show=True),
    ]

    def compose(self) -> ComposeResult:
        yield Header()
        yield Static(id="library-hint")
        yield DataTable(id="library-table", zebra_stripes=True)
        with Horizontal(classes="button-row"):
            yield Button("Crop…", id="btn-crop", variant="primary")
            yield Button("Copy markdown", id="btn-copy")
            yield Button("Re-encode", id="btn-reencode")
            yield Button("Delete", id="btn-delete", variant="error")
            yield Button("Open folder", id="btn-open")
            yield Button("Back", id="btn-back")
        yield RichLog(id="library-log", highlight=True, markup=True)
        yield Footer()

    def on_mount(self) -> None:
        table = self.query_one("#library-table", DataTable)
        table.add_columns("Kind", "Clip", "File", "Size", "Modified")
        self.reload_table()

    def on_screen_resume(self) -> None:
        self.reload_table()

    def action_reload(self) -> None:
        self.reload_table()

    def action_pop_screen(self) -> None:
        self.app.pop_screen()

    @on(Button.Pressed, "#btn-back")
    def _back(self) -> None:
        self.action_pop_screen()

    @on(Button.Pressed, "#btn-copy")
    def _copy_btn(self) -> None:
        self.action_copy_markdown()

    @on(Button.Pressed, "#btn-reencode")
    def _reencode_btn(self) -> None:
        self.action_reencode()

    @on(Button.Pressed, "#btn-delete")
    def _delete_btn(self) -> None:
        self.action_delete_file()

    @on(Button.Pressed, "#btn-open")
    def _open_btn(self) -> None:
        self.action_open_folder()

    @on(Button.Pressed, "#btn-crop")
    def _crop_btn(self) -> None:
        self.action_open_crop()

    def reload_table(self) -> None:
        repo: Path = self.app.repo  # type: ignore[attr-defined]
        items = list_media(repo)
        self._items = items
        table = self.query_one("#library-table", DataTable)
        table.clear()
        for item in items:
            table.add_row(
                item.kind,
                item.clip_name,
                item.path.name,
                human_size(item.size),
                item.mtime_label,
                key=str(item.path),
            )
        hint = self.query_one("#library-hint", Static)
        if items:
            hint.update(
                f"{len(items)} file(s) — select a raw (or any clip), then Crop / copy / delete."
            )
        else:
            hint.update("No media yet. Record something from the Record screen.")

    def _selected_by_cursor(self) -> MediaFile | None:
        table = self.query_one("#library-table", DataTable)
        if not hasattr(self, "_items") or not self._items:
            return None
        row_idx = table.cursor_row
        if row_idx is None or row_idx < 0 or row_idx >= len(self._items):
            return None
        return self._items[row_idx]

    def _resolve_raw(self, item: MediaFile) -> Path | None:
        repo: Path = self.app.repo  # type: ignore[attr-defined]
        if item.kind == "raw":
            return item.path
        return find_raw_for_clip(repo, item.clip_name)

    def action_open_crop(self) -> None:
        item = self._selected_by_cursor()
        log = self.query_one("#library-log", RichLog)
        if not item:
            log.write("[yellow]Select a row first.[/yellow]")
            return
        raw = self._resolve_raw(item)
        if not raw:
            log.write(
                f"[yellow]No raw for clip '{item.clip_name}'. "
                "Crop needs a *-raw-*.mp4.[/yellow]"
            )
            return
        self.app.push_screen(CropScreen(raw, item.clip_name))

    def action_copy_markdown(self) -> None:
        item = self._selected_by_cursor()
        log = self.query_one("#library-log", RichLog)
        if not item or item.kind != "gif":
            log.write("[yellow]Select a GIF row to copy README markdown.[/yellow]")
            return
        text = markdown_snippet(item.clip_name)
        try:
            subprocess.run(
                ["xclip", "-selection", "clipboard"],
                input=text,
                text=True,
                check=True,
                timeout=2,
            )
            log.write(f"[green]Copied:[/green] {text}")
        except (FileNotFoundError, subprocess.CalledProcessError):
            try:
                subprocess.run(
                    ["wl-copy"],
                    input=text,
                    text=True,
                    check=True,
                    timeout=2,
                )
                log.write(f"[green]Copied (wl-copy):[/green] {text}")
            except (FileNotFoundError, subprocess.CalledProcessError):
                log.write(f"Clipboard unavailable. Snippet:\n  {text}")

    def action_open_folder(self) -> None:
        repo: Path = self.app.repo  # type: ignore[attr-defined]
        folder = media_dir(repo)
        opened = False
        for cmd in (
            ["xdg-open", str(folder)],
            ["dolphin", str(folder)],
            ["nautilus", str(folder)],
        ):
            if shutil.which(cmd[0]):
                subprocess.Popen(cmd)
                opened = True
                break
        log = self.query_one("#library-log", RichLog)
        if opened:
            log.write(f"Opened {folder}")
        else:
            log.write(f"Path: {folder}")

    def action_delete_file(self) -> None:
        item = self._selected_by_cursor()
        log = self.query_one("#library-log", RichLog)
        if not item:
            log.write("[yellow]Select a row first.[/yellow]")
            return
        try:
            item.path.unlink()
            log.write(f"[green]Deleted[/green] {item.path.name}")
            self.reload_table()
        except OSError as exc:
            log.write(f"[red]Delete failed:[/red] {exc}")

    def action_reencode(self) -> None:
        item = self._selected_by_cursor()
        log = self.query_one("#library-log", RichLog)
        if not item:
            log.write("[yellow]Select a raw MP4 row to re-encode.[/yellow]")
            return
        if item.kind != "raw":
            log.write("[yellow]Re-encode works on raw recordings only (or use Crop).[/yellow]")
            return
        self._run_reencode(item)

    @work(thread=True)
    def _run_reencode(self, item: MediaFile) -> None:
        repo: Path = self.app.repo  # type: ignore[attr-defined]
        log = self.query_one("#library-log", RichLog)
        seconds = probe_duration_seconds(item.path)

        def on_line(text: str) -> None:
            self.app.call_from_thread(log.write, text)

        code = run_encode(repo, item.path, item.clip_name, seconds, on_line)
        if code == 0:
            self.app.call_from_thread(self.reload_table)
        else:
            self.app.call_from_thread(log.write, f"[red]Re-encode failed ({code}).[/red]")


class MediaStudioApp(App):
    """BlowTorch phone demo recorder."""

    CSS = """
    Screen {
        layout: vertical;
    }
    #status-scroll, #record-form, #crop-form {
        height: 1fr;
        padding: 1 2;
    }
    .section-title {
        margin-top: 1;
        text-style: bold;
    }
    .hint {
        color: $text-muted;
        margin: 1 0;
    }
    .button-row {
        height: auto;
        margin: 1 0;
    }
    .button-row Button {
        margin-right: 1;
    }
    .xywh-row {
        height: auto;
        margin-bottom: 1;
    }
    .xywh-row .coord {
        width: 1fr;
        margin-right: 1;
    }
    #library-table {
        height: 1fr;
        margin: 0 2;
    }
    #library-log, #record-log, #crop-log {
        height: 8;
        margin: 0 2 1 2;
        border: solid $primary;
    }
    #library-hint {
        margin: 0 2;
        color: $text-muted;
    }
    #quickstart {
        margin: 0 0 1 0;
    }
    #preset {
        margin-bottom: 1;
    }
    """

    TITLE = "BlowTorch Media Studio"
    SUB_TITLE = "README demos"

    BINDINGS = [
        Binding("q", "quit", "Quit", show=True),
        Binding("?", "help", "Help", show=True),
    ]

    SCREENS = {
        "status": StatusScreen,
        "record": RecordScreen,
        "library": LibraryScreen,
    }

    def __init__(self, repo: Path) -> None:
        super().__init__()
        self.repo = repo

    def on_mount(self) -> None:
        self.push_screen("status")

    def action_help(self) -> None:
        self.notify(
            "r=Record  l=Library  x=Crop  c=Copy MD  e=Re-encode  d=Delete  o=Folder",
            timeout=6,
        )


def main() -> None:
    parser = argparse.ArgumentParser(description="BlowTorch Media Studio TUI")
    parser.add_argument(
        "--repo",
        type=Path,
        default=None,
        help="BlowTorch repo root (auto-detected if omitted)",
    )
    args = parser.parse_args()
    repo = args.repo or find_repo_root()
    MediaStudioApp(repo).run()


if __name__ == "__main__":
    main()
