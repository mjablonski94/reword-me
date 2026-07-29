# RewordMe

![Platform](https://img.shields.io/badge/platform-macOS%2014%2B-blue)
![Swift](https://img.shields.io/badge/Swift-6-orange)
![License](https://img.shields.io/badge/license-MIT-green)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-%E2%98%95-yellow)](https://buymeacoffee.com/kofcio94f)

A tiny macOS menu-bar app that rewrites your selected text with an LLM. Select text in any app,
press **Option+Command+R**, and a floating popup appears with a reworded version - regenerate it,
steer the next generation ("more formal"), copy it, or replace the selection in place. Bring your
own API key for Claude (Anthropic), ChatGPT (OpenAI) or Gemini (Google); by default RewordMe uses
the least costly model your provider offers.

- Repository: https://github.com/mjablonski94/reword-me
- Platform: macOS 14 (Sonoma) and later
- Language: Swift 6 (SwiftUI + AppKit)

---

## Table of contents

- [Features](#features)
- [How it works](#how-it-works)
- [Install](#install)
- [Permissions](#permissions)
- [Usage](#usage)
- [Configuration](#configuration)
- [Architecture](#architecture)
- [Development](#development)
- [Distribution](#distribution)
- [Troubleshooting](#troubleshooting)
- [Privacy](#privacy)
- [Support](#support)
- [License](#license)

---

## Features

- **Menu-bar only** (no Dock icon). Works over any app: Mail, Slack, browsers, editors.
- **Global hotkey**: select text anywhere, press **Option+Command+R**, the popup appears at the
  selection.
- **Auto-popup by default**: the popup appears the moment you finish selecting text with the
  mouse, PopClip-style. Prefer it quieter? Switch the trigger in Settings > General to
  hotkey-only.
- **Services menu**: right-click selected text > Services > *Reword with RewordMe* - this path
  needs no Accessibility permission at all.
- **Writing-Tools-style popup**: a menu-first panel beside the selection - a "describe your
  change" field, Proofread and Rewrite actions, and Friendly / Professional / Concise presets.
  Nothing is sent to the model until you pick an action, so the auto-popup costs nothing.
- **Replace animation**: on Replace, the panel shrinks into the text it just rewrote.
- **Floating, non-activating popup**: shows the rewrite without stealing focus from the app you
  are writing in, so your selection stays alive underneath.
- **Frosted UI**: the popup is a borderless, semi-transparent frosted sheet (no window chrome,
  no outline), with native glass buttons on macOS 26 (Tahoe) and bordered fallbacks on 14/15.
- **Regenerate and steer**: not happy with the result? Type a one-shot instruction like
  *"make it more formal"* and regenerate. Steering applies to that generation only.
- **Replace in place** (via Accessibility, with a clipboard-paste fallback) or **Copy**.
- **Do's and don'ts**: a toggleable list of standing rules (*"Never use exclamation marks"*,
  *"Keep it under two sentences"*) sent with every rewrite.
- **Base prompt**: freeform standing instructions (*"I am a non-native speaker; fix grammar but
  keep my voice."*).
- **Multi-provider**: Claude, ChatGPT, Gemini, Mistral, Grok (xAI), DeepSeek - just paste one
  API key (stored in the login Keychain, never in plain files) - or **Ollama** for fully local,
  free rewrites where the text never leaves your machine.
- **Model choice**: pick any model the provider lists, or leave it on **Automatic**, which
  resolves to the least costly tier (Haiku / nano / Flash-Lite / Ministral / grok-mini /
  deepseek-chat) so everyday rewrites stay cheap.
- **Launch at login** (start with the system) via `SMAppService`.

## How it works

1. **Trigger** - a Carbon global hotkey (`RegisterEventHotKey`, no permission needed) or the
   macOS Services menu entry.
2. **Read the selection** - first through the Accessibility API (`AXSelectedText` of the focused
   element); if the app does not expose it (many Electron apps, browser web content), RewordMe
   synthesizes Cmd+C, reads the pasteboard, and **restores your previous clipboard** right after.
3. **Popup** - a non-activating `NSPanel` positioned at the selection bounds (or at the mouse),
   hosting a SwiftUI view. Because it never activates RewordMe, the host app keeps focus and the
   selection.
4. **Rewrite** - the system prompt is assembled from a fixed core instruction, your enabled
   do/don't rules, your base prompt, and the optional one-shot steering line; the selected text is
   sent as the user message over plain HTTPS to the provider you configured.
5. **Replace** - sets `AXSelectedText` directly when the focused element allows it; otherwise it
   pastes over the selection and restores your clipboard afterwards.

## Install

### From source

```bash
git clone https://github.com/mjablonski94/reword-me.git
cd reword-me
./build.sh
open RewordMe.app
```

Move `RewordMe.app` to `/Applications` if you want it permanent.

## Permissions

| Permission | Needed for | Notes |
|---|---|---|
| **Accessibility** | Reading the selection on the hotkey path and replacing it in place | Prompted on first launch. Grant in System Settings > Privacy & Security > Accessibility. |
| *(none)* | The Services-menu path and Copy | Right-click > Services > Reword with RewordMe works without any permission. |

Ad-hoc signed builds get a new signature every rebuild, so macOS drops the Accessibility grant
after `./build.sh` - re-grant once. A Developer ID build keeps the grant.

## Usage

1. Open **Settings** from the menu-bar icon, pick a provider and paste its API key, then
   **Save Key**. The Provider tab links straight to the right console:
   - Claude: https://platform.claude.com/settings/keys
   - OpenAI: https://platform.openai.com/api-keys
   - Gemini: https://aistudio.google.com/apikey (free tier available)
   - Mistral: https://console.mistral.ai/api-keys (free tier available)
   - Grok (xAI): https://console.x.ai
   - DeepSeek: https://platform.deepseek.com/api_keys
   - Ollama: no key at all - install from https://ollama.com, `ollama pull llama3.2`, done.
     The server address is configurable (defaults to `http://localhost:11434`).
2. Select text in any app.
3. Press **Option+Command+R** (or right-click > Services > *Reword with RewordMe*).
4. In the popup: pick **Proofread**, **Rewrite**, a tone preset, or type your own instruction
   ("make it sound less angry") and press Return. Then **Again** for another take, **Replace**
   to swap the selection, **Copy** to take it with you. Esc closes.

## Configuration

Settings live in three tabs:

- **Provider** - provider picker, API key (Keychain), model picker. *Automatic (least costly)*
  fetches the provider's model list and picks the cheapest family - Claude Haiku, GPT nano/mini,
  Gemini Flash-Lite - preferring stable releases over previews. Pick an explicit model any time;
  **Load Models** shows everything your key can access.
- **Rewriting** - the do/don't rules list (each rule toggleable) and the freeform base prompt.
- **General** - the trigger (hotkey, or automatic on every mouse text selection), launch at
  login, Accessibility status.

Non-secret settings are stored as JSON at
`~/Library/Application Support/RewordMe/config.json`. API keys are stored only in the login
Keychain (service `com.mjablonski.rewordme`).

The system prompt is assembled per request as:

```
1. Core instruction        (fixed: rewrite, preserve meaning/language, output only the text)
2. Do: / Don't: rules      (your enabled rules)
3. Base prompt             (your freeform standing instructions)
4. One-shot steering       (typed in the popup, this generation only)
```

## Architecture

```
Sources/
├── RewordMeCore/            pure logic, fully unit-tested, no AppKit
│   ├── Provider.swift       provider kinds, model info, typed errors
│   ├── AnthropicAPI.swift   request building + response parsing (Messages API)
│   ├── OpenAICompatibleAPI.swift  the chat-completions dialect: OpenAI, Mistral, xAI, DeepSeek
│   ├── GeminiAPI.swift      request building + response parsing (generateContent)
│   ├── ModelSelection.swift least-costly default model heuristic
│   ├── PromptBuilder.swift  core + rules + base prompt + steering assembly
│   ├── RewordConfig.swift   Codable settings + JSON store
│   └── RewordService.swift  URLSession calls, HTTP error mapping (401/429/5xx)
└── RewordMeApp/             the menu-bar app
    ├── AppDelegate.swift    status item, wiring
    ├── HotkeyManager.swift  Carbon global hotkey
    ├── SelectionReader.swift  AX selection + Cmd+C fallback with clipboard restore
    ├── TextReplacer.swift   AX replace + Cmd+V fallback with clipboard restore
    ├── PopupController.swift  non-activating NSPanel, positioning
    ├── PopupView.swift      SwiftUI popup UI
    ├── RewordSession.swift  popup state + generation flow
    ├── SettingsWindow.swift Provider / Rewriting / General tabs
    ├── SettingsModel.swift  settings state, model listing, launch at login
    ├── KeychainStore.swift  API keys in the login Keychain
    ├── ModelResolver.swift  caches the automatic model pick per provider
    ├── ServicesProvider.swift  Services-menu entry
    └── AccessibilityPermission.swift
```

## Development

```bash
swift build          # debug build
swift test           # unit tests (RewordMeCore)
./build.sh           # release .app bundle, ad-hoc signed
./build.sh debug     # debug .app bundle
```

Rate limits (HTTP 429) and invalid keys are surfaced as readable messages in the popup, including
the provider's `retry-after` hint when present.

The app icon is generated from code: `swift make-icon.swift && iconutil -c icns AppIcon.iconset
-o AppIcon.icns`.

## Distribution

```bash
./make-dmg.sh        # drag-to-install DMG from the current build (ad-hoc, for testing)
./dist.sh            # Developer ID signed + notarized + stapled release DMG
```

`dist.sh` needs an Apple Developer Program membership:

```bash
export DEVELOPER_ID="Developer ID Application: Your Name (TEAMID)"
export NOTARY_PROFILE="your-notarytool-keychain-profile"
```

## Troubleshooting

- **The popup says "No text selected"** - the frontmost app exposes no AX selection and blocked
  the Cmd+C fallback. Check that Accessibility is granted; the Services-menu path always works.
- **Replace does nothing** - some apps accept neither AX writes nor synthetic Cmd+V. Use Copy.
- **"The API key was rejected"** - re-paste the key in Settings and Save Key. Gemini keys start
  with `AIza`, Anthropic with `sk-ant-`, OpenAI with `sk-`. Keys are created in each provider's
  console (linked from the Provider tab), not in the chat apps themselves.
- **"Rate limit reached"** - the provider throttled the key; the popup shows the retry hint.
  Consider a cheaper model tier (Automatic already picks the cheapest).
- **The hotkey keeps asking for Accessibility even though it looks enabled** - the System
  Settings entry belongs to a previous build: every rebuild from source gets a fresh ad-hoc
  signature, and macOS treats it as a new app while still showing the old, now-meaningless
  entry as ON. Remove RewordMe from the Accessibility list with the minus button and add the
  current app again. `./build.sh` now resets the stale grant automatically
  (`tccutil reset Accessibility com.mjablonski.rewordme`), so after a rebuild the list shows
  the truth: not granted yet.
- **macOS asks for my password to access the Keychain** - that is the Keychain protecting your
  stored API key: the prompt comes from macOS, and the key never leaves your Mac. Choose
  "Always Allow" and it will not ask again. Builds from source are re-signed on every rebuild,
  so macOS treats each rebuild as a new app and asks once more; a Developer ID build asks once.

## Privacy

- The selected text is sent **only** to the provider you configured, over HTTPS, with your own
  API key. There is no middleman, no telemetry, no analytics. With **Ollama** the text never
  leaves your machine at all.
- API keys are stored in the macOS login Keychain and sent only in request headers - never in
  URLs, never on disk in plain text.
- The clipboard is used only as a fallback and is restored to its previous contents immediately.
- Nothing else leaves your machine.

## Support

RewordMe is free and MIT-licensed. If it saves you time, you can
[buy me a coffee](https://buymeacoffee.com/kofcio94f) - also reachable from the menu-bar menu
and Settings > General.

Bugs and ideas: [open an issue](https://github.com/mjablonski94/reword-me/issues).

## License

MIT - see [LICENSE](LICENSE).
