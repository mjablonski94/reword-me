import AppKit
import RewordMeCore
import SwiftUI

@MainActor
final class SettingsWindowController {
    private let window: NSWindow

    init() {
        let model = SettingsModel()
        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 560, height: 480),
            styleMask: [.titled, .closable, .miniaturizable],
            backing: .buffered,
            defer: false
        )
        window.title = "RewordMe Settings"
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
    @ObservedObject var model: SettingsModel

    var body: some View {
        TabView {
            ProviderSettingsView(model: model)
                .tabItem { Label("Provider", systemImage: "key") }
            RewritingSettingsView(model: model)
                .tabItem { Label("Rewriting", systemImage: "pencil.line") }
            GeneralSettingsView(model: model)
                .tabItem { Label("General", systemImage: "gearshape") }
        }
        .frame(width: 560, height: 480)
    }
}

// MARK: - Provider tab

struct ProviderSettingsView: View {
    @ObservedObject var model: SettingsModel

    var body: some View {
        Form {
            Section("Provider") {
                Picker("Provider", selection: $model.config.provider) {
                    ForEach(ProviderKind.allCases) { kind in
                        Text(kind.displayName).tag(kind)
                    }
                }
                .pickerStyle(.menu)
            }

            if model.config.provider.requiresAPIKey {
                Section("API key") {
                    SecureField(model.config.provider.keyPlaceholder, text: $model.apiKey)
                    HStack {
                        Link(
                            "Get an API key at \(model.config.provider.apiKeyConsoleName)",
                            destination: model.config.provider.apiKeyConsoleURL
                        )
                        .font(.caption)
                        Spacer()
                        if model.keySavedFeedback {
                            Label("Saved", systemImage: "checkmark.circle.fill")
                                .foregroundStyle(.green)
                                .transition(.opacity)
                        }
                        Button("Save Key") { model.saveAPIKey() }
                            .disabled(model.apiKey.isEmpty)
                    }
                    .animation(.easeInOut(duration: 0.2), value: model.keySavedFeedback)
                    Text("Stored in your login Keychain, never in plain files. If macOS asks whether RewordMe may access the Keychain, that is your Mac guarding the key - choose Always Allow.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } else {
                Section("Local server") {
                    Text("Ollama runs on your Mac - no API key, no cost, and the text never leaves your machine.")
                        .font(.callout)
                    TextField("Server", text: $model.config.ollamaHost, prompt: Text(OllamaEndpoint.defaultHost))
                    Text("Default is \(OllamaEndpoint.defaultHost) - change it only if Ollama listens elsewhere (OLLAMA_HOST, Docker port mapping, another machine).\nInstall it, then pull a model, e.g.:  ollama pull llama3.2")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Link(
                        "Get Ollama at \(model.config.provider.apiKeyConsoleName)",
                        destination: model.config.provider.apiKeyConsoleURL
                    )
                    .font(.caption)
                }
            }

            Section("Model") {
                Picker("Model", selection: $model.config.model) {
                    Text("Automatic (least costly)").tag(String?.none)
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
                    Button("Load Models") { model.loadModels() }
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
    @ObservedObject var model: SettingsModel

    var body: some View {
        Form {
            Section {
                ForEach($model.config.rules) { $rule in
                    HStack(spacing: 8) {
                        Toggle("", isOn: $rule.isEnabled)
                            .labelsHidden()
                        Picker("", selection: $rule.kind) {
                            ForEach(RuleKind.allCases, id: \.self) { kind in
                                Text(kind.label).tag(kind)
                            }
                        }
                        .labelsHidden()
                        .frame(width: 80)
                        TextField("e.g. Never use exclamation marks", text: $rule.text)
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
                    Label("Add Rule", systemImage: "plus")
                }
            } header: {
                Text("Do's and don'ts")
            } footer: {
                Text("Each enabled rule is sent with every rewrite. Toggle rules off instead of deleting them when they only sometimes apply.")
            }

            Section {
                TextEditor(text: $model.config.basePrompt)
                    .font(.body)
                    .frame(minHeight: 120)
            } header: {
                Text("Base prompt")
            } footer: {
                Text("Freeform standing instructions, e.g. \"I am a non-native speaker; fix grammar but keep my voice.\"")
            }
        }
        .formStyle(.grouped)
    }
}

// MARK: - General tab

struct GeneralSettingsView: View {
    @ObservedObject var model: SettingsModel
    @State private var accessibilityTrusted = AccessibilityPermission.isTrusted

    private let timer = Timer.publish(every: 2, on: .main, in: .common).autoconnect()

    var body: some View {
        Form {
            Section("Trigger") {
                Picker("Show the popup", selection: $model.config.triggerMode) {
                    Text("Automatically on text selection").tag(TriggerMode.selection)
                    Text("Only when pressing Option+Command+R").tag(TriggerMode.hotkey)
                }
                .pickerStyle(.radioGroup)
                if model.config.triggerMode == .selection {
                    Text("The popup appears whenever you finish selecting text with the mouse, in any app. Needs Accessibility access.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Text("Also available from the Services menu: right-click selected text, then Services > Reword with RewordMe.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Startup") {
                Toggle("Launch at login", isOn: $model.launchAtLogin)
            }

            Section("Permissions") {
                LabeledContent("Accessibility") {
                    if accessibilityTrusted {
                        Label("Granted", systemImage: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                    } else {
                        Button("Open System Settings") {
                            AccessibilityPermission.openSystemSettings()
                        }
                    }
                }
                Text("Needed to read the selected text and to replace it in place. Without it, only the Services menu and Copy work.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Support") {
                Link(
                    "Buy me a coffee",
                    destination: URL(string: "https://buymeacoffee.com/kofcio94f")!
                )
            }
        }
        .formStyle(.grouped)
        .onReceive(timer) { _ in
            accessibilityTrusted = AccessibilityPermission.isTrusted
        }
    }
}
