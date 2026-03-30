# Blacks BlackBox - [▣] ᚠᛟᚱᚲ

Android app virtualization fork focused on modern Android compatibility and practical validation workflows.

**In short**: Android 14+ support, Android 16 network/compat hardening, QoL improvements, and slight privacy hardening!


## Why this fork? + Scope

I made this fork to make the apps I actually care about usable in a virtualized setup.

- Priority target: BlackBox working in Work Profile + Instagram support

- Not everything works yet (and it might never fully do). This fork is deliberately focused on apps I use.

- I *might* take requests if an app is genuinely needed.
- No game support for now (it’s a whole different level of work).


## Changes vs BlackBox & NewBlackbox

- Work profiles now work 100% in my validation path

- Work-profile-aware smoke tooling available (see smoke_install_launch.sh) that targets the correct Android user/profile and produces logs + screenshots.
- This was necessary because the old workflow was painful: install host → install target inside → debug one issue → repeat... (Super Annoying)

- Android 14+/16 compatibility hardening via additional/updated system-service hooks (e.g. Credential Manager, IME/InputMethodManager, telephony registry)

- Privacy-first behavior tightened in PackageManager surfaces, with an explicit opt-in compatibility fallback toggle (`host_signing_fallback`)

## Changelog
### 30.03.26 - 4.6 Update: Freezing, Device Spoofing, and Media Sharing

#### Gallery . ݁₊ ⊹ . ݁˖ . ݁
Added a ```Send to BlackBox Gallery``` share target that lets users send photos and videos from the phone’s gallery into the virtualized B-Box.

Also includes a barebones gallery app inside Black's B-Box. It even plays videos!
Barebones, but it works!

#### Freeze ₊˚｡⋆❆⋆｡˚₊

Added a freezer button that lets you instantly freeze and kill a running app.

Ending Instagram doomscrolling with a single click. It is satisfying to do to be honest.
>Settings -> ```Freeze App instantly```

Also added an auto-freeze feature that can automatically freeze apps when you leave BlackBox.
>Main view -> Snowflake button

Lastly you can choose per app whether it should be excluded from auto-freeze, so selected apps stay running while everything else is frozen automatically. A bit janky, but it works.

>Enable auto-freeze -> Long-press app in Black’s B-Box -> ```Freeze Options```

#### 𝔻𝕖𝕧𝕚𝕔𝕖 𝕊𝕡𝕠𝕠𝕗𝕚𝕟𝕘

Added device spoofing, so apps inside BlackBox can see a custom device model, manufacturer, and Android ID.
>Main view -> More options menu -> ```Device Spoofing```
---
#### 
### 27.03.26
Brave Browser now runs inside Black’s B-Box on Android 16 (launches successfully and reaches the home/new-tab screen).

## Current status

- Builds successfully with `:app:assembleDebug`
- Targets physical ARM64 devices as the primary test path
- Smoke install+launch flow validated for main + work profiles
- VPN mode intentionally disabled on Android 14+ until full forwarding is implemented, it was simply not working


## Project goals (maybe)

- Improve Android 14+/16 app compatibility (system-service enforcement + crash fixes)
- Keep validation practical: smoke install/launch runs that produce logs + screenshots
- Support both main profile and work profiles (multi-user aware)
- Prefer privacy-first defaults, with explicit opt-in compatibility fallbacks when needed


## Quick start

```bash
./gradlew :app:assembleDebug

bash tools/smoke_install_launch.sh \
  --serial YOUR_DEVICE_SERIAL \
  --android-user-id 0 \
  --user-id 0 \
  --package-name com.instagram.android \
  --minutes 1 \
  /path/to/APK.apk
```

## Credits

- Black00Z - Blacks BlackBox
- ALEX5402 - NewBlackbox
- asLody - VirtualApp
- didi - VirtualAPK
- jmpews - Dobby
- hexhacking - xDL
- CodingGay - BlackReflection
- tiann - FreeReflection

Made with (a lot of) Legacy Code and AI.
