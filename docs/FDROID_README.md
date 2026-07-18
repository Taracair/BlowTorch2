# BlowTorch 2 — F-Droid notes (permissions & privacy)

This page is for **F-Droid users and reviewers**. It explains what the app does, why each Android permission exists, and what “All files access” actually means here.

Longer build/submission steps: [`fdroid.md`](fdroid.md).  
General project intro: [`../README.md`](../README.md).

---

## What this app is

**BlowTorch 2** is a **MUD client**: it opens a TCP connection to a text game server you choose, shows the game text, and lets you type commands. Players often keep local automation (triggers, aliases, timers), on-screen button panels, and Lua plugins.

It is a continuation of the classic open-source BlowTorch client (MIT). It is **not** a commercial product, **not** an ad network, and **not** a cloud sync service.

Package id (F-Droid / production): `com.resurrection.blowtorch2`

---

## What the app does **not** do

- Does **not** upload your settings, passwords, logs, or game text to any BlowTorch “cloud”.
- Does **not** include analytics, crash-reporting SDKs, ads, or trackers.
- Does **not** need an account with us.
- Does **not** read your photos, contacts, SMS, or call log.
- Does **not** browse your whole disk for fun — file access is for **your** BlowTorch folders and files you explicitly import/export.

Optional **account notes** on a launcher entry (login/password/mail) are stored **only on this device**, in the launcher list XML, in **plaintext**. They are reminders for you, not auto-login. Prefer leaving them blank. Android **backup is disabled** (`allowBackup=false`) so cloud/ADB backup will not pull that XML; exports/backups you create yourself still include whatever you typed.

### Lua plugins (important)

Installing or running a **Lua plugin** is equivalent to running **untrusted code with the app’s privileges** (same model as classic BlowTorch). Plugins can use the full Lua standard library and are given access to Android `Context` / activity helpers. Only load plugins you trust. Do not treat the plugin folder as a sandbox.

---

## Network

The app connects **only** to hosts **you** configure in the launcher (MUD hostname + port).  
There is no mandatory phone-home to the BlowTorch 2 project.

---

## Shared folder layout

When shared storage is available, user-visible files default to:

```text
/storage/emulated/0/BlowTorch/
  settings/       # import/export session settings XML
  backups/        # zip backups from the launcher
  launcher/       # server list export/import
  session_logs/   # optional .txt logs of incoming game text
  logs/           # local error / GMCP debug log (blowtorch2.log)
```

Without broad storage access, the same layout is created under the app’s own external directory (under `Android/data/…`), which is harder to reach from a file manager. You can also pick folders/files with the system picker (**SAF**) for many import/export actions.

---

## Permission table

| Permission | Required to play? | Why it exists |
|------------|-------------------|---------------|
| **Internet** | **Yes** | Talk to the MUD server you chose. |
| **Access network state** | Helpful | Know when the network is up; normal for a network client. |
| **Access Wi‑Fi state** | Optional | Used with “keep Wi‑Fi alive” style behaviour while connected. |
| **Vibrate** | Optional | Bell / alert vibration if you enable those options. |
| **Post notifications** (Android 13+) | Optional but useful | Ongoing connection notification; trigger/timer notification responders. |
| **Foreground service** (+ specialUse) | **Yes** for background play | Keep the MUD connection alive when the screen is off / app is in the background. Without this, Android often kills the session. |
| **Wake lock** | Related | Reduce the chance the CPU sleeps while a session should stay up. |
| **Request ignore battery optimizations** | Optional | Opens the system dialog so you can exempt BlowTorch if the OEM kills background apps aggressively. You can refuse; the app still works, but long sessions may drop. |
| **Read / write external storage** (older Android, ≤ 12) | Optional | Classic storage access for `/BlowTorch/` on older API levels. |
| **Manage external storage** (“All files access”, Android 11+) | **Optional** | See next section. |

---

## About “All files access” (the scary one)

Android shows a strong warning for `MANAGE_EXTERNAL_STORAGE`. That is fair: the permission *can* allow an app to touch many files on shared storage.

**In BlowTorch 2 it is used for one practical goal:** create and use a normal, visible folder tree at **`/BlowTorch/`** (settings, backups, launcher lists, session logs, debug logs) the way older MUD clients and desktop players expect — files you can copy with a PC, Termux, or a file manager **without** digging into `Android/data`.

### What we use it for

- Creating `/BlowTorch/` and the subfolders listed above  
- Writing session `.txt` logs and the local `blowtorch2.log` there  
- Default import/export/backup paths under that tree  

### What we do **not** use it for

- Scanning your SD card for media or personal documents  
- Reading unrelated apps’ private data  
- Uploading files anywhere  

### Do you have to grant it?

**No.** You can play MUDs with only network + notification/foreground-service permissions.

If you refuse All files access:

- The client still connects and plays.  
- Files fall back under the app-specific storage tree (same subfolder names).  
- You can still use **Browse… / Pick file… / Choose location…** (SAF) for many import/export flows.  

Grant All files access only if you want the convenient shared `/BlowTorch/` path.

How to grant: **Options → Miscellaneous → Manage Storage Access** (opens the system screen). You can revoke it later in Android Settings → Apps → BlowTorch 2 → Permissions / Special app access.

### Why not only SAF?

The Storage Access Framework (folder picker) is more privacy-friendly and is already used for many pickers. A **SAF-only** default (one-time “choose or create BlowTorch folder”, then remember that tree) is a possible future direction and would avoid declaring All files access.

Today, All files access is the straightforward way to keep a stable absolute path (`/BlowTorch/...`) that matches player habits and the classic BlowTorch mental model. We document it openly so F-Droid users can decide.

---

## Privacy summary (short)

| Topic | Behaviour |
|-------|-----------|
| Telemetry / ads | None in the production app |
| Accounts | None with the BlowTorch 2 project |
| Data leaving the device | Only your MUD TCP traffic (and whatever that game’s server does) |
| Local settings | On device; backup/export only when you ask |
| Source | Fully open (MIT); F-Droid builds from tagged source |

---

## For F-Droid maintainers

- Recipe draft: [`../metadata/com.resurrection.blowtorch2.yml`](../metadata/com.resurrection.blowtorch2.yml)  
- Fastlane store text: [`../fastlane/metadata/android/en-US/`](../fastlane/metadata/android/en-US/)  
- Production flavor only (`gradle: [production]`); do **not** ship `btTest` from this recipe.  
- `MANAGE_EXTERNAL_STORAGE` is declared; please treat it as **optional UX for shared `/BlowTorch/`**, not as a requirement to run the client.  
- No proprietary libraries in the production dependency graph.  

If F-Droid policy prefers dropping All files access, say so in the MR — we can prioritize a SAF-rooted default and remove the permission in a follow-up release.

---

## Contact

Bugs and permission UX feedback: [GitHub Issues](https://github.com/Taracair/BlowTorch2/issues).
