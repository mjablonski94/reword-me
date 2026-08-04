# RewordMe - Kotlin Multiplatform (Windows, Linux, Android)

The KMP sibling of the [macOS app](../macos/): Compose Multiplatform desktop app targeting
**Windows first**, with Linux and Android (`ACTION_PROCESS_TEXT`) planned on the same core.

## Status: 1.0.1, released for Windows

Linux and Android remain planned on the same core.

Working:
- Tray icon with menu (Reword Selection, Settings, Buy Me a Coffee, Quit)
- Global hotkey via `RegisterHotKey`, default **Ctrl+Alt+R**, recordable in Settings.
  Registration failures and conflicts open Settings instead of failing silently - without a
  working shortcut there is no way into the app
- Selection capture: synthesized Ctrl+C with clipboard save/restore
- Popup window, pre-warmed at launch so the first hotkey press pays no composition cost:
  menu-first UI (describe field, Proofread/Rewrite, tone presets), result view with
  Again/Copy/Replace. The frame follows the content as stages change, and the header strip
  doubles as a title bar so the undecorated window can still be dragged
- Replace in place: focus restore to the host window + paste + clipboard restore
- DWM acrylic backdrop + rounded corners + tool-window style (no taskbar/Alt-Tab entry),
  solid translucent fallback where acrylic is unavailable
- Settings in three tabs (Provider, Rewriting, General): API/account/local setup, endpoint, model
  pick, rules and base prompt, shortcut recorder, launch at login. Styled as the macOS
  grouped `Form` it mirrors - segmented tab picker on a toolbar strip, sections as cards of
  hairline-divided rows, label left and control right - down to the greys, which are sampled
  off the screenshots in `macos/docs/media`. The title bar is dark so the window has no seam
- Launch at login is **on by default**: the app registers itself for startup on first run,
  because a tray app that is not running cannot answer its shortcut. The answer is recorded
  in `config.json`, so switching it off sticks. macOS does the same via `SMAppService`
- API keys in Windows Credential Manager, migrated off the phase-1 plaintext file on first run
- Localized into 10 languages besides English
- App icon drawn in code, shared by the tray glyph, the window icon and the packaged `.ico`
- All providers, unfiltered and ordered consistently with macOS: Gemini (recommended), Offline
  models (local), OpenAI API, Codex via ChatGPT, Claude API, Claude account, Mistral, Grok, DeepSeek,
  and Ollama. Each provider remembers its own model selection
- Managed offline-model catalog with pinned revisions/SHA-256 digests, per-model selection,
  determinate progress, cancel/retry/remove, and bundled verified x64 + ARM64 llama.cpp Windows
  runtimes—no Ollama installation required. The catalog includes Qwen, Google Gemma, Hugging Face
  SmolLM3, and Mistral Ministral options; model source and license links are visible in Settings
- Separate direct-API and subscription-account execution. Codex and Claude detect their official
  local executable, redirect to official setup when missing, and leave authentication token
  ownership entirely with that executable

Still planned: UI Automation `TextPattern` (clipboard-free reads + a selection rectangle to
place the popup against), Linux support, Android Process Text.

## Settings

| Provider | General |
|---|---|
| <img src="docs/media/settings-provider.png" alt="Provider tab: provider pop-up, API key, model picker"> | <img src="docs/media/settings-general.png" alt="General tab: editable shortcut, launch at login, support"> |

Captured from the real window by `./gradlew :desktop:app:settingsShots`, which refuses to
write a shot unless the pixels prove it is the settings window on the expected tab - a screen
grab otherwise photographs whatever happens to be in front.

## Architecture

Layered clean architecture, Gradle modules, dependencies pointing inward only:

```
core:models        pure value types (KMP, commonMain)
core:domain        business rules + ports: PromptBuilder, ModelSelection, ModelResolver,
                   ConfigStore and ApiKeyStore interfaces
core:data          IO: Ktor clients, API/account dispatch, managed model/runtime lifecycle,
                   JSON config, and the plaintext key store used as the non-Windows fallback
desktop:platform   Win32 via JNA: hotkey, SendInput, focus tracking, DWM acrylic,
                   Credential Manager, run-at-login
desktop:app        Compose UI (MVVM) + composition root
```

## Build and run

```bash
./gradlew build                     # compiles + runs all tests (works on any OS)
./gradlew :desktop:app:run          # run (Windows for real behavior; dev stubs elsewhere)
./gradlew :desktop:app:packageMsi   # Windows installer (run on Windows)
./gradlew :desktop:app:packageExe   # standalone installer
```

For the Windows 1.0.1 release, install a 64-bit JDK 21 and WiX Toolset 3.x, make sure
`JAVA_HOME` and WiX's `bin` directory are on `PATH`, then run this from PowerShell:

```powershell
cd kmp
.\package-windows.ps1
```

The script runs the full build and tests, downloads and verifies the pinned x64/ARM64 local AI
runtimes, creates the EXE installer, verifies the configured version, and copies
`RewordMe-1.0.1.exe` plus its SHA-256 file into the `kmp` directory. Follow
[`WINDOWS_TEST_CHECKLIST.md`](WINDOWS_TEST_CHECKLIST.md) for the PC handoff.

Verification helpers, all writing into `desktop/app/build/`:

```bash
./gradlew :desktop:app:renderUi           # every screen rendered offscreen to PNGs
./gradlew :desktop:app:probePopupWindow   # drives the real window, prints the frame per stage
./gradlew :desktop:app:makeAppIcon        # regenerates the committed icons/AppIcon.ico
./gradlew :desktop:app:settingsShots      # README screenshots from the real settings window
```

`renderUi` measures content only. Anything about the window itself - frame sizing, the acrylic
backdrop, dragging - needs `probePopupWindow`, which drives the real thing.

Config lives in `%APPDATA%\RewordMe\config.json`; API keys live in Windows Credential Manager
under `RewordMe/<provider>`.

## License

RewordMe is MIT-licensed—see [LICENSE](../LICENSE). Downloadable models retain their own licenses;
their source and license links appear in Settings and in the repository's
[third-party notices](../THIRD_PARTY_NOTICES.txt).
