# Ringtone volume-silence fix — implementation summary

**Cursor plan:** `~/.cursor/plans/ringtone_silence_fix_d19ff7d2.plan.md`  
**Goal:** Gelen aramada Telecom `START_RINGER` sonrası ~300 ms içinde yanlış `silenceRinger()` çağrısını durdur.

## Root cause

`VolumeSilence` her `VOLUME_CHANGED` ve tüm `Settings.System` değişikliklerinde susturuyor. Telecom zil başlarken `setStreamVolume(STREAM_RING)` atınca kullanıcı tuşu sanılıyor.

## Fix

1. Grace ~1 sn (gerekirse 1.5 sn) after `register()`
2. Only `STREAM_RING` + volume **decrease** (prev empty → only `curr == 0` / known prior decrease)
3. Remove global `Settings.System.CONTENT_URI` observer; optional ring-volume URI only if needed
4. Pure `VolumeSilencePolicy.shouldSilence(...)` + JVM tests
5. Keep manual silence paths (key / swipe / notification)

## Verify

- Fake or real incoming: no auto `SILENCE:TSI.sR` after `START_RINGER`
- Volume-down still silences; notification stays
- Preview unlocked + locked smoke
