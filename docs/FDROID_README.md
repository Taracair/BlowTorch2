# BlowTorch 2 — F-Droid notes (permissions & privacy)

For F-Droid users and reviewers: what the app does, why permissions exist, and what All files access means here.

Build steps: [`fdroid.md`](fdroid.md).  
Project intro: [`../README.md`](../README.md).

---

## What this app is

BlowTorch 2 is a MUD client. It opens a TCP connection to a text game you choose, shows the text, and lets you type. People often keep triggers, aliases, timers, on-screen buttons, and Lua plugins.

It is a fork of the classic open-source BlowTorch client (MIT). Not a commercial product, not ads, not cloud sync.

Package id: `com.resurrection.blowtorch2`

---

## What it does not do

- Does not upload your settings, passwords, logs, or game text to any BlowTorch cloud
- No analytics, crash SDKs, ads, or trackers
- No account with us
- Does not read photos, contacts, SMS, or call log
- Does not scan unrelated files — storage is for BlowTorch folders and files you import/export

**Account notes** (login/password/mail on a launcher row) are optional reminders stored as plain text in the launcher list on this device. Not auto-login. Leave blank if you don’t want that. Android backup is off (`allowBackup=false`). Your own export/backup files will still include whatever you typed.

### Lua plugins

A Lua plugin runs with the app’s privileges (same as classic BlowTorch): full Lua libs plus Android helpers. Only load plugins you trust. The plugin folder is not a sandbox.

---

## Network

Connects only to hosts you add in the launcher. No phone-home to the BlowTorch 2 project.

---

## Shared folder layout

When shared storage is available:

```text
/storage/emulated/0/BlowTorch/
  settings/       # session settings XML
  backups/        # zip backups
  launcher/       # server list
  session_logs/   # optional game text logs
  logs/           # blowtorch2.log
```

Without All files access, the same layout lives under the app’s own folder (harder to reach from a file manager). You can still use the system file picker (SAF) for many import/export actions.

---

## Permission table

| Permission | Required to play? | Why |
|------------|-------------------|-----|
| Internet | Yes | Talk to the MUD |
| Access network state | Helpful | Know when the network is up |
| Access Wi‑Fi state | Optional | Keep Wi‑Fi awake while connected |
| Vibrate | Optional | Bell / alerts |
| Post notifications (Android 13+) | Optional but useful | Connection notification; trigger/timer alerts |
| Foreground service (+ specialUse) | Yes for background play | Keep the session alive when the screen is off |
| Wake lock | Related | Less chance the CPU sleeps mid-session |
| Ignore battery optimizations | Optional | OEM helpers that kill background apps; you can refuse |
| Read/write external storage (≤ Android 12) | Optional | Classic `/BlowTorch/` access |
| Manage external storage (All files access, 11+) | Optional | See below |

---

## All files access

Android warns hard about `MANAGE_EXTERNAL_STORAGE` because it can reach a lot of shared storage.

Here it is only used to keep a normal visible `/BlowTorch/` tree (settings, backups, launcher, logs) that you can copy with a PC or file manager without digging into `Android/data`.

Used for:
- Creating `/BlowTorch/` and those subfolders
- Session logs and `blowtorch2.log`
- Default import/export/backup paths

Not used for:
- Scanning media or personal documents
- Reading other apps’ private data
- Uploading files

You do **not** have to grant it. You can play with network + notification/foreground-service permissions. Without it, files go under app-specific storage; Browse / Pick file still work for many flows.

Grant only if you want the shared `/BlowTorch/` path.  
**Options → Miscellaneous → Manage Storage Access**. Revoke anytime in system app settings.

SAF (folder picker) is already used for many pickers and is more privacy-friendly. A SAF-only default later would avoid declaring All files access; for now the absolute `/BlowTorch/` path matches how a lot of players already work.

---

## Privacy summary

| Topic | Behaviour |
|-------|-----------|
| Telemetry / ads | None |
| Accounts with us | None |
| Data leaving the device | Only your MUD TCP traffic |
| Local settings | On device; backup/export when you ask |
| Source | Open (MIT); F-Droid builds from tags |

---

## For F-Droid maintainers

- Recipe: [`../metadata/com.resurrection.blowtorch2.yml`](../metadata/com.resurrection.blowtorch2.yml)
- Store text: [`../fastlane/metadata/android/en-US/`](../fastlane/metadata/android/en-US/)
- Production flavor only (`gradle: [production]`) — do not ship `btTest`
- Treat `MANAGE_EXTERNAL_STORAGE` as optional UX, not required to play
- No proprietary libs in the production graph

If policy wants All files access gone, say so in the MR and we can move to a SAF-rooted default.

---

## Contact

[GitHub Issues](https://github.com/Taracair/BlowTorch2/issues)
