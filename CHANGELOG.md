# Changelog

All notable changes to RewordMe are documented in this file.

The project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Added an Android app draft built on the shared KMP core, with an
  `ACTION_PROCESS_TEXT` selection-menu workflow, launcher tutorial, settings,
  encrypted API-key storage, and 11 interface languages.
- Added a downloadable Android 1.0.0 development APK for cable-free testing on
  Android 8.0 or newer devices, together with its SHA-256 checksum.
- Animated the Android popup: it fades in and out over the host app, the
  card morphs to each stage's size, and stage content cross-fades.
- Added an on-device end-to-end suite for Android (UiAutomator against a mock
  provider), run green on every supported version from Android 8.0 to 16.

### Fixed

- Fixed Gemini's automatic model choice picking a text-to-speech model, which
  made every rewrite fail with "Developer instruction is not enabled"; speech,
  image, transcription, agent and Gemma models are no longer offered.
- Reworked the Android popup for small screens, large fonts and longer
  languages: a full-width primary action, a scrollable card, a visible close
  button, a preview of the selection and a localized model label.
- Added an Android setup state and "Settings" shortcuts in the popup so a
  missing or rejected key is no longer a dead end.
- Added RewordMe to the Android share sheet: since Android 11 many apps
  (Messages, Gmail, Contacts) cannot list "Reword me" in their selection menu,
  but every one offers Share; the result comes back by Copy.
- Made browser selections on Android usable: when the host cannot take the
  rewrite back, Copy becomes the primary action with an explanation, and
  Replace from a browser also copies the text as a fallback.
- Kept Android results from being lost to the back gesture or a stray tap,
  showed friendly network and timeout errors, confirmed copies on Android
  versions without a system clipboard notice, and hinted at slow providers.
- Fixed stale keyboard space on Android 8-10 screens, explained where to find
  "Reword me" in selection menus, and explained Ollama addresses on phones.
- Fixed the Android popup opening the keyboard over its own result on
  Android 8-9 after a stage change.
- Hardened the Android draft after review, including atomic configuration
  writes, serialized Keystore access, request timeouts, user-configured Ollama
  cleartext support, edge-to-edge/keyboard insets, dark Material menus,
  cancellation handling, accessibility labels, and settings persistence.

## [1.0.1] - 2026-08-04

### Added

- Added distinct provider choices for OpenAI API, Codex via a ChatGPT account,
  Claude API, and Claude via a Claude account. Account-based providers detect
  their required official command-line app and offer setup guidance when it is
  unavailable.
- Added **Offline models (Local)** as the second provider after the recommended
  Gemini option. RewordMe can download, verify, run, cancel, and remove supported
  GGUF models without requiring Ollama.
- Added a managed offline-model catalog with Qwen 3.5 0.8B, Gemma 3 1B IT,
  Qwen 3 1.7B, SmolLM3 3B, Ministral 3 3B Instruct, and Qwen 3 4B options.
- Added model download progress, cancellation, checksum verification, per-model
  installation state, source information, and license links.
- Added the selected provider and model to the rewrite popup, including the
  selected offline model when local inference is active.
- Added the application version to Settings.
- Added third-party model and runtime notices, provider/offline-model screenshots,
  and a Windows 1.0.1 build-and-test checklist.

### Changed

- Kept Gemini first in the provider list and marked it as recommended; kept
  Ollama as an external-local option for users who already run it.
- Stored model selection separately for each provider so switching providers no
  longer carries over an incompatible model choice.
- Replaced the separate Do/Don't rule types with a single **Rules** list while
  retaining migration support for existing configuration files.
- Improved provider descriptions, setup states, errors, and localized interface
  text across the macOS and Kotlin Multiplatform applications.
- Revised Polish translations for more natural wording and synchronized the
  other supported locale resources with the new interface.

### Fixed

- Fixed managed local inference continuing indefinitely or returning an empty
  response when a model emitted hidden reasoning instead of rewritten text.
- Fixed a macOS SwiftUI crash caused by rapidly typing in a newly added focused
  rule and then removing that rule.
- Fixed stale bindings in the Rules editor by resolving rows through stable
  identifiers and clearing focus before deletion.
- Fixed provider/model state leaking between Gemini, API, account, offline, and
  Ollama configurations.
- Improved selection capture, replacement, provider response parsing, process
  execution, and error handling on macOS and Windows.

### Release notes

- macOS 14 or later is supported by the native Swift application.
- The Kotlin Multiplatform Windows source and packaging workflow target version
  1.0.1. The Windows installer must still be built and validated on Windows by
  following `kmp/WINDOWS_TEST_CHECKLIST.md`.
- Downloadable model weights are not bundled with RewordMe and retain their own
  licenses. See `THIRD_PARTY_NOTICES.txt` for details.

[Unreleased]: https://github.com/mjablonski94/reword-me/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/mjablonski94/reword-me/releases/tag/v1.0.1
