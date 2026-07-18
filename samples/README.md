# Local profile samples (not in git)

Profile XML and launcher lists stay on your machine/device.
They are gitignored — do not commit them.

Example local names:

| File | Purpose |
|------|---------|
| `test.xml` | Scratch / test profile |
| `my_mud.xml` | Your real profile |
| `blowtorch_launcher_list.xml` | Launcher connection list |

Push to a device (example):

```bash
adb push test.xml /data/local/tmp/
adb shell run-as com.resurrection.blowtorch2 cp /data/local/tmp/test.xml files/
```

Or use import/export in the app launcher.
