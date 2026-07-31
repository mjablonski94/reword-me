# RewordMe - Kotlin Multiplatform (Windows, Linux, Android)

The KMP sibling of the [macOS app](../macos/): Compose Multiplatform desktop app targeting
**Windows first**, with Linux and Android (`ACTION_PROCESS_TEXT`) planned on the same core.

## Status: phase 1 (walking skeleton)

Working:
- Tray icon with menu (Reword Selection, Settings, Buy Me a Coffee, Quit)
- Global hotkey **Ctrl+Alt+R** via `RegisterHotKey` (no permission needed on Windows)
- Selection capture: synthesized Ctrl+C with clipboard save/restore
- Pre-warmed popup window: menu-first UI (describe field, Proofread/Rewrite, tone presets),
  result view with Again/Copy/Replace
- Replace in place: focus restore to the host window + paste + clipboard restore
- DWM acrylic backdrop + rounded corners + tool-window style (no taskbar/Alt-Tab entry),
  solid translucent fallback where acrylic is unavailable
- All 7 providers (Claude, ChatGPT, Gemini, Mistral, Grok, DeepSeek, Ollama) with the
  least-costly automatic model pick, same prompt assembly as macOS

Phase 2 (planned): UI Automation TextPattern (clipboard-free reads + selection rectangle),
Windows Credential Manager for keys, shortcut recorder, rules/base-prompt settings UI,
localization, app icon, Linux support, Android Process Text.

## Architecture

Layered clean architecture, Gradle modules, dependencies pointing inward only:

```
core:models        pure value types (KMP, commonMain)
core:domain        business rules + ports: PromptBuilder, ModelSelection, ModelResolver
core:data          IO: Ktor provider clients, RewordService, config/key stores
desktop:platform   Win32 via JNA: hotkey, SendInput, focus tracking, DWM acrylic
desktop:app        Compose UI (MVVM) + composition root
```

## Build and run

```bash
./gradlew build          # compiles + runs all tests (works on any OS)
./gradlew :desktop:app:run             # run (Windows for real behavior; dev stubs elsewhere)
./gradlew :desktop:app:packageMsi     # Windows installer (run on Windows)
```

Keys are stored in `%APPDATA%\RewordMe\keys.json` in phase 1 (Credential Manager planned);
config in `%APPDATA%\RewordMe\config.json`.
