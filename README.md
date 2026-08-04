# RewordMe

![License](https://img.shields.io/badge/license-MIT-green)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-%E2%98%95-yellow)](https://buymeacoffee.com/kofcio94f)

Select text in any app, press a shortcut, and a small popup rewrites it - proofread, friendlier,
more professional, or steered by your own instruction - then replaces it in place. Use Gemini
(recommended), a provider API key, an existing ChatGPT/Claude subscription through its official
installed app, an optional one-click offline model, or an existing Ollama server. No
tracking and no RewordMe middleman account.

![RewordMe demo](macos/docs/media/demo.gif)

## Platforms

| Platform | Directory | Stack | Status |
|---|---|---|---|
| **macOS** | [`macos/`](macos/) | Swift 6, SwiftUI + AppKit | **Released** - v1.0.1, macOS 14+, 11 languages |
| **Windows** | [`kmp/`](kmp/) | Kotlin Multiplatform, Compose Desktop | **Source-complete** - v1.0.1 installer and native Windows validation pending |
| Linux | [`kmp/`](kmp/) | same KMP codebase | Planned |
| Android | [`kmp/`](kmp/) | same KMP codebase (Process Text) | Planned |

Each platform README has its own build and install instructions.

## Pick how RewordMe runs

<p align="center">
  <img src="macos/docs/media/provider-picker.png" width="201" alt="RewordMe provider picker with Gemini recommended first, Offline models second, API and account providers, and Ollama">
  &nbsp;&nbsp;&nbsp;
  <img src="macos/docs/media/offline-model-picker.png" width="326" alt="RewordMe offline model picker with Qwen, Google Gemma, Hugging Face SmolLM3, and Mistral Ministral models">
</p>

<p align="center"><em>Use the simplest cloud setup, a provider API, an existing subscription account, RewordMe-managed offline models, or Ollama.</em></p>

## Offline models

**Offline models (Local)** is the second provider after Gemini. Models are optional, downloaded
individually, checksum-verified, and run through RewordMe's bundled llama.cpp runtime—no account,
API key, Ollama, or internet connection is needed after download. Multiple models may remain
installed, while the model picker determines which one serves the next rewrite.

| Model | Model maker | Download | Intended use | License |
|---|---|---:|---|---|
| Qwen 3.5 0.8B Q4 | Qwen | 537 MiB | Fastest | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Gemma 3 1B IT Q4 | Google | 769 MiB | Compact alternative | [Gemma Terms](https://ai.google.dev/gemma/terms) |
| Qwen 3 1.7B Q4 | Qwen | 1.19 GiB | Balanced | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| SmolLM3 3B Q4 | Hugging Face | 1.78 GiB | English-focused | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Ministral 3 3B Instruct Q4 | Mistral AI | 2.00 GiB | Quality alternative | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Qwen 3 4B Q4 | Qwen | 2.33 GiB | Best multilingual quality | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |

The app shows the selected model's source and license in Settings. Exact immutable revisions,
byte counts, and SHA-256 digests are recorded in the platform catalogs. The model weights are
downloaded directly from their repositories and are not included in RewordMe installers; see
[third-party notices](THIRD_PARTY_NOTICES.txt).

## Support

RewordMe is free and MIT-licensed. If it saves you time, you can
[buy me a coffee](https://buymeacoffee.com/kofcio94f).

Bugs and ideas: [open an issue](https://github.com/mjablonski94/reword-me/issues).

Release history: [CHANGELOG.md](CHANGELOG.md).

## License

RewordMe is MIT-licensed—see [LICENSE](LICENSE). Downloadable models retain their own licenses;
see [third-party notices](THIRD_PARTY_NOTICES.txt).
