# Local profile samples (not in git)

MUD profile XML and launcher list files belong on your device or machine only.
They are **gitignored** and must not be committed.

Typical local files (create or copy from a backup):

| File | Purpose |
|------|---------|
| `test.xml` | Test-server profile |
| `samsaramoo.xml` | Production profile |
| `blowtorch_launcher_list.xml` | Launcher connection list |

Push to a device (example):

```bash
adb push test.xml /data/local/tmp/
adb shell run-as com.resurrection.blowtorch2 cp /data/local/tmp/test.xml files/
```

Or use import/export in the app launcher.
