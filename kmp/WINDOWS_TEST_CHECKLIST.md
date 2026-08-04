# Windows 1.0.1 build and test checklist

Run this after the macOS changes have been pushed and pulled onto the Windows PC.

## Build the EXE

1. Install 64-bit JDK 21 and WiX Toolset 3.x (`candle.exe` and `light.exe` must be on `PATH`).
2. Open PowerShell, pull the release branch, and point `JAVA_HOME` at JDK 21.
3. From the repository's `kmp` directory, run:

   ```powershell
   .\package-windows.ps1
   ```

4. Confirm it produces `kmp\RewordMe-1.0.1.exe` and
   `kmp\RewordMe-1.0.1.exe.sha256`. Keep the complete PowerShell output if it fails.

## Installer and core UI

- Install over any earlier RewordMe build; verify only one app entry remains.
- Open Settings > General and confirm the displayed version is `1.0.1`.
- Confirm the tray icon, Ctrl+Alt+R shortcut, Copy, Replace, launch-at-login toggle, and Quit.
- Open Settings > Rewriting. Confirm the section is named **Rules** and every row contains only an
  enable toggle, editable rule text, and Remove—there must be no Do/Don't selector. Existing saved
  positive and negative rules must remain present after upgrading.
- Stress the rule editor: repeatedly add a rule, type immediately, and remove it while its text
  field is still focused. Settings must stay open and the other rules must remain unchanged.
- Confirm the provider picker contains all ten entries in this exact order:
  Gemini (Recommended), Offline models (Local), OpenAI API, Codex via ChatGPT, Claude API,
  Claude via Claude account, Mistral, Grok (xAI), DeepSeek, Ollama (External local).

## Provider-specific models

- Load Gemini models and select one explicit model.
- Switch to OpenAI API: Gemini's model must disappear and OpenAI must show Automatic/its own model.
- Select an OpenAI model, switch back to Gemini, and confirm the Gemini selection returns.
- Restart RewordMe and repeat the switch; both choices must still be tied to their providers.

## Managed offline models

- Choose Offline models (Local). Confirm Qwen, Google Gemma, Hugging Face SmolLM3, and Mistral
  Ministral options appear, each with its maker, download size, model-details link, and license.
- Select Qwen 3.5 0.8B and start the roughly 537 MiB (563 MB) download.
- Confirm the progress bar is determinate and downloaded/total sizes keep updating.
- Cancel partway through. Restart the app and confirm it does not claim the partial file is ready.
- Retry to completion; checksum verification must finish and status must become Ready.
- Switch the picker to another model and back. Qwen must remain Ready; downloads are per-model,
  while the currently selected model—not a global model value—must serve the next rewrite.
- Disconnect the internet and rewrite text. It must work locally without Ollama running.
- Quit RewordMe and confirm no `llama-server.exe` process remains.
- Relaunch, use the local model again, then click Remove Model and verify Download is offered again.

## API and account access

- Gemini: the previously saved key should still work and remains the recommended first option.
- OpenAI API and Claude API: keys and billing remain separate from account/subscription choices.
- Codex via ChatGPT:
  - without Codex/ChatGPT installed, Setup must open the official website;
  - after installation, Refresh must detect it;
  - Sign In must use the ChatGPT account and a rewrite must succeed without an OpenAI API key.
- Claude via Claude account: repeat the same missing/install/sign-in/rewrite checks with official
  Claude Code. Ensure `ANTHROPIC_API_KEY` is not forcing API billing during this subscription test.
- Ollama: confirm it is still present, accepts a configurable server URL, loads models, and rewrites.

## Report back

Send the EXE build output plus any failed checklist item, screenshot, and popup error text. Do not
publish the installer until both the macOS and Windows smoke tests pass.
