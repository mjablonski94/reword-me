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
            contentRect: NSRect(x: 0, y: 0, width: 580, height: 560),
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
        .frame(width: 580, height: 560)
    }
}

// MARK: - Provider tab

struct ProviderSettingsView: View {
    @ObservedObject var model: SettingsViewModel

    var body: some View {
        Form {
            Section(Loc.providerSection) {
                Picker(
                    Loc.providerSection,
                    selection: Binding(
                        get: { model.config.provider },
                        set: model.selectProvider
                    )
                ) {
                    ForEach(ProviderKind.allCases) { kind in
                        Text(kind.displayName).tag(kind)
                    }
                }
                .pickerStyle(.menu)
                .disabled(model.isLocalDownloadActive)
            }

            switch model.config.provider.access {
            case .apiKey:
                Section(Loc.apiKeySection) {
                    SecureField(
                        model.config.provider.keyPlaceholder,
                        text: Binding(get: { model.apiKey }, set: model.editAPIKey)
                    )
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
                            .disabled(!model.canSaveAPIKey)
                    }
                    .animation(.easeInOut(duration: 0.2), value: model.keySavedFeedback)
                    if let error = model.keySaveError {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .font(.caption)
                            .foregroundStyle(.red)
                            .transition(.opacity)
                    }
                    Text(Loc.keychainCaption)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            case .account:
                accountSection
            case .managedLocal:
                localModelSection
            case .externalLocal:
                Section(Loc.ollamaSection) {
                    Text(Loc.ollamaBlurb)
                        .font(.callout)
                    TextField(
                        Loc.ollamaServer,
                        text: Binding(
                            get: { model.config.ollamaHost },
                            set: model.setOllamaHost
                        ),
                        prompt: Text(OllamaEndpoint.defaultHost)
                    )
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

            if model.config.provider.access != .managedLocal {
                Section(Loc.modelSection) {
                    Picker(Loc.modelLabel, selection: $model.config.model) {
                        Text(Loc.automaticModel).tag(String?.none)
                        ForEach(
                            model.availableModels.filter { $0.id != "automatic" }
                                .sorted { $0.id < $1.id }
                        ) { modelInfo in
                            Text(modelInfo.displayName).tag(String?.some(modelInfo.id))
                        }
                        // Keep a previously chosen model selectable before the list loads.
                        if let current = model.config.model,
                           current != "automatic",
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
                        if model.config.provider.access == .apiKey ||
                            model.config.provider.access == .externalLocal {
                            Button(Loc.loadModels) { model.loadModels() }
                                .disabled(
                                    (model.config.provider.requiresAPIKey &&
                                        model.apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                                        || model.isLoadingModels
                                )
                        }
                    }
                }
            }
        }
        .formStyle(.grouped)
        .onAppear { model.refreshProviderSetup() }
    }

    @ViewBuilder
    private var accountSection: some View {
        Section(Loc.accountSection) {
            Text(Loc.accountBlurb(model.config.provider.displayName))
                .font(.callout)

            if model.isCheckingAccount || model.isSigningIn {
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small)
                    Text(model.isSigningIn ? Loc.accountSigningIn : Loc.accountChecking)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } else if let status = model.accountStatus {
                if !status.isInstalled {
                    Label(Loc.accountNotInstalled, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                } else if status.isAuthenticated {
                    Label(Loc.accountConnected, systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                } else if status.usesAPIKey {
                    Label(Loc.accountAPIBilling, systemImage: "exclamationmark.triangle.fill")
                        .font(.caption)
                        .foregroundStyle(.orange)
                } else {
                    Label(Loc.accountNotConnected, systemImage: "person.crop.circle.badge.exclamationmark")
                        .foregroundStyle(.orange)
                }
                if let version = status.version, !version.isEmpty {
                    Text(version).font(.caption.monospaced()).foregroundStyle(.secondary)
                }
            }

            if let error = model.accountError {
                Label(error, systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(.red)
            }

            HStack {
                Link(
                    Loc.accountSetupGuide,
                    destination: model.config.provider.apiKeyConsoleURL
                )
                .font(.caption)
                Spacer()
                Button(Loc.refresh) { model.refreshProviderSetup() }
                    .disabled(model.isCheckingAccount || model.isSigningIn)
                Button(model.accountStatus?.isInstalled == false ? Loc.install : Loc.signIn) {
                    model.setUpAccountProvider()
                }
                .disabled(model.isCheckingAccount || model.isSigningIn)
            }
        }
    }

    @ViewBuilder
    private var localModelSection: some View {
        Section(Loc.localSection) {
            Text(Loc.localBlurb)
                .font(.callout)
            Picker(Loc.modelLabel, selection: Binding(
                get: { model.selectedLocalModel.id },
                set: model.selectLocalModel
            )) {
                ForEach(LocalModelCatalog.all) { manifest in
                    Text("\(manifest.displayName) — \(manifest.maker) · \(model.formattedBytes(manifest.byteCount))")
                        .tag(manifest.id)
                }
            }
            .pickerStyle(.menu)
            .disabled(model.isLocalDownloadActive)

            let selected = model.selectedLocalModel
            HStack(spacing: 10) {
                Text("\(selected.maker) · \(model.formattedBytes(selected.byteCount))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Link(Loc.localSource, destination: selected.informationURL)
                Link(selected.licenseName, destination: selected.licenseURL)
            }
            .font(.caption)

            switch model.localModelState {
            case .notDownloaded:
                HStack {
                    Spacer()
                    Button(Loc.localDownload) { model.downloadLocalModel() }
                }
            case let .downloading(progress):
                VStack(alignment: .leading, spacing: 7) {
                    ProgressView(value: progress.fraction)
                        .progressViewStyle(.linear)
                    HStack {
                        Text(model.localProgressText)
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                        Spacer()
                        Button(Loc.cancel) { model.cancelLocalModelDownload() }
                    }
                }
            case .ready:
                HStack {
                    Label(Loc.localReady, systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                    Spacer()
                    Button(Loc.localRemove, role: .destructive) { model.removeLocalModel() }
                }
            case let .failed(message):
                Label(message, systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(.red)
                HStack {
                    Spacer()
                    Button(Loc.localRetry) { model.downloadLocalModel() }
                }
            }
        }
    }
}

// MARK: - Rewriting tab

struct RewritingSettingsView: View {
    @ObservedObject var model: SettingsViewModel
    @FocusState private var focusedRuleID: UUID?

    var body: some View {
        Form {
            Section {
                // Never bind a text field through Array's index projection.
                // AppKit can deliver a final edit callback after its focused
                // row is removed; an index binding then traps if the array is
                // shorter. UUID lookups safely ignore that late callback.
                ForEach(model.config.rules) { rule in
                    HStack(spacing: 8) {
                        Toggle("", isOn: ruleBinding(
                            id: rule.id,
                            keyPath: \.isEnabled,
                            fallback: false
                        ))
                            .labelsHidden()
                        TextField(
                            Loc.rulePlaceholder,
                            text: ruleBinding(id: rule.id, keyPath: \.text, fallback: "")
                        )
                        .focused($focusedRuleID, equals: rule.id)
                        Button {
                            if focusedRuleID == rule.id { focusedRuleID = nil }
                            model.removeRule(id: rule.id)
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

    private func ruleBinding<Value>(
        id: UUID,
        keyPath: WritableKeyPath<RewriteRule, Value>,
        fallback: Value
    ) -> Binding<Value> {
        Binding(
            get: { model.rule(id: id)?[keyPath: keyPath] ?? fallback },
            set: { newValue in
                model.updateRule(id: id) { $0[keyPath: keyPath] = newValue }
            }
        )
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

            Section(Loc.aboutSection) {
                LabeledContent(Loc.version) {
                    Text(AppVersion.display)
                        .monospacedDigit()
                        .textSelection(.enabled)
                }
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

private enum AppVersion {
    static var display: String {
        let bundle = Bundle.main
        let version = bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString")
            as? String ?? "—"
        guard let build = bundle.object(forInfoDictionaryKey: "CFBundleVersion") as? String,
              !build.isEmpty,
              build != version else {
            return version
        }
        return "\(version) (\(build))"
    }
}
