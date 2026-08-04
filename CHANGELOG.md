# Changelog

All notable changes to RewordMe are documented in this file.

The project follows [Semantic Versioning](https://semver.org/).

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

[1.0.1]: https://github.com/mjablonski94/reword-me/releases/tag/v1.0.1
