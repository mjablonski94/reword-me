import AppKit
import RewordMeModels
import RewordMePlatform
import SwiftUI

@MainActor
final class SettingsWindowController {
    private let window: NSWindow

    init(dependencies: AppDependencies) {
        let model = SettingsViewModel(dependencies: dependencies)
        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 560, height: 480),
            styleMask: [.titled, .closable, .miniaturizable],
            backing: .buffered,
            defer: false
        )
        window.title = Loc.settingsTitle
        window.contentViewController = NSHostingController(rootView: SettingsView(model: model))
        window.isReleasedWhenClosed = false
        window.center()
        self.window = window
    }

    func show() {
        NSApp.activate(ignoringOtherApps: true)
        window.makeKeyAndOrderFront(nil)
    }
}

struct SettingsView: View {
    @ObservedObject var model: SettingsViewModel

    var body: some View {
        TabView {
            ProviderSettingsView(model: model)
                .tabItem { Label(Loc.tabProvider, systemImage: "key") }
            RewritingSettingsView(model: model)
                .tabItem { Label(Loc.tabRewriting, systemImage: "pencil.line") }
            GeneralSettingsView(model: model)
                .tabItem { Label(Loc.tabGeneral, systemImage: "gearshape") }
        }
        .frame(width: 560, height: 480)
    }
}

// MARK: - Provider tab

struct ProviderSettingsView: View {
    @ObservedObject var model: SettingsViewModel

    var body: some View {
        Form {
            Section(Loc.providerSection) {
                Picker(Loc.providerSection, selection: $model.config.provider) {
                    ForEach(ProviderKind.allCases) { kind in
                        Text(kind.displayName).tag(kind)
                    }
                }
                .pickerStyle(.menu)
            }

            if model.config.provider.requiresAPIKey {
                Section(Loc.apiKeySection) {
                    SecureField(model.config.provider.keyPlaceholder, text: $model.apiKey)
                    HStack {
                        Link(
                            Loc.getKey(model.config.provider.apiKeyConsoleName),
                            destination: model.config.provider.apiKeyConsoleURL
                        )
                        .font(.caption)
                        Spacer()
                        if model.keySavedFeedback {
                            Label(Loc.saved, systemImage: "checkmark.circle.fill")
                                .foregroundStyle(.green)
                                .transition(.opacity)
                        }
                        Button(Loc.saveKey) { model.saveAPIKey() }
                            .disabled(model.apiKey.isEmpty)
                    }
                    .animation(.easeInOut(duration: 0.2), value: model.keySavedFeedback)
                    Text(Loc.keychainCaption)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } else {
                Section(Loc.ollamaSection) {
                    Text(Loc.ollamaBlurb)
                        .font(.callout)
                    TextField(Loc.ollamaServer, text: $model.config.ollamaHost, prompt: Text(OllamaEndpoint.defaultHost))
                    Text(Loc.ollamaCaption(OllamaEndpoint.defaultHost))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Link(
                        Loc.getOllama(model.config.provider.apiKeyConsoleName),
                        destination: model.config.provider.apiKeyConsoleURL
                    )
                    .font(.caption)
                }
            }

            Section(Loc.modelSection) {
                Picker(Loc.modelLabel, selection: $model.config.model) {
                    Text(Loc.automaticModel).tag(String?.none)
                    ForEach(model.availableModels) { modelInfo in
                        Text(modelInfo.id).tag(String?.some(modelInfo.id))
                    }
                    // Keep a previously chosen model selectable before the list loads.
                    if let current = model.config.model,
                       !model.availableModels.contains(where: { $0.id == current }) {
                        Text(current).tag(String?.some(current))
                    }
                }
                HStack {
                    if model.isLoadingModels {
                        ProgressView().controlSize(.small)
                    } else if let error = model.modelsError {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                    } else if !model.automaticModelHint.isEmpty {
                        Text(model.automaticModelHint)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button(Loc.loadModels) { model.loadModels() }
                        .disabled(
                            (model.config.provider.requiresAPIKey && model.apiKey.isEmpty)
                                || model.isLoadingModels
                        )
                }
            }
        }
        .formStyle(.grouped)
    }
}

// MARK: - Rewriting tab

struct RewritingSettingsView: View {
    @ObservedObject var model: SettingsViewModel

    var body: some View {
        Form {
            Section {
                ForEach($model.config.rules) { $rule in
                    HStack(spacing: 8) {
                        Toggle("", isOn: $rule.isEnabled)
                            .labelsHidden()
                        Picker("", selection: $rule.kind) {
                            ForEach(RuleKind.allCases, id: \.self) { kind in
                                Text(kind == .doRule ? Loc.ruleDo : Loc.ruleDont).tag(kind)
                            }
                        }
                        .labelsHidden()
                        .frame(width: 80)
                        TextField(Loc.rulePlaceholder, text: $rule.text)
                        Button {
                            model.removeRule(rule)
                        } label: {
                            Image(systemName: "minus.circle")
                        }
                        .buttonStyle(.plain)
                    }
                }
                Button {
                    model.addRule()
                } label: {
                    Label(Loc.addRule, systemImage: "plus")
                }
            } header: {
                Text(Loc.rulesSection)
            } footer: {
                Text(Loc.rulesFooter)
            }

            Section {
                TextEditor(text: $model.config.basePrompt)
                    .font(.body)
                    .frame(minHeight: 120)
            } header: {
                Text(Loc.basePromptSection)
            } footer: {
                Text(Loc.basePromptFooter)
            }
        }
        .formStyle(.grouped)
    }
}

// MARK: - General tab

struct GeneralSettingsView: View {
    @ObservedObject var model: SettingsViewModel
    @StateObject private var recorder = HotkeyRecorder()

    private let timer = Timer.publish(every: 2, on: .main, in: .common).autoconnect()

    var body: some View {
        Form {
            Section(Loc.shortcutSection) {
                HStack {
                    Text(Loc.shortcutLabel)
                    Spacer()
                    Button {
                        if recorder.isRecording {
                            recorder.cancel()
                        } else {
                            recorder.begin { hotkey in
                                model.config.hotkey = hotkey
                            }
                        }
                    } label: {
                        Text(recorder.isRecording ? Loc.shortcutRecording : model.config.hotkey.display)
                            .font(.body.monospaced())
                            .frame(minWidth: 110)
                    }
                }
                Text(recorder.isRecording ? Loc.shortcutHintRecording : Loc.shortcutHintIdle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(Loc.servicesHint)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section(Loc.startupSection) {
                Toggle(Loc.launchAtLogin, isOn: $model.launchAtLogin)
            }

            Section(Loc.permissionsSection) {
                LabeledContent(Loc.accessibility) {
                    if model.accessibilityTrusted {
                        Label(Loc.granted, systemImage: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                    } else {
                        Button(Loc.openSystemSettings) {
                            model.openAccessibilitySettings()
                        }
                    }
                }
                Text(Loc.accessibilityCaption)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section(Loc.supportSection) {
                Link(
                    Loc.buyCoffee,
                    destination: URL(string: "https://buymeacoffee.com/kofcio94f")!
                )
            }
        }
        .formStyle(.grouped)
        .onReceive(timer) { _ in
            model.refreshAccessibilityStatus()
        }
    }
}
