import RewordMeCore
import SwiftUI

/// Writing-Tools-style panel: a describe field with an intelligence glow,
/// two big actions, tone presets - and the result view after generating.
struct PopupView: View {
    @ObservedObject var session: RewordSession
    @FocusState private var steeringFocused: Bool

    private let intelligenceGradient = AngularGradient(
        colors: [.blue, .purple, .pink, .orange, .blue],
        center: .center
    )

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            header
            stageContent
                .animation(.spring(duration: 0.3), value: session.stage)
        }
        .padding(14)
        .frame(width: 320)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.ultraThinMaterial)
        )
    }

    // MARK: - Header

    private var header: some View {
        HStack(spacing: 6) {
            if session.stage == .result || isFailed {
                Button {
                    session.backToMenu()
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(.secondary)
                        .frame(width: 20, height: 20)
                        .background(.quaternary.opacity(0.5), in: Circle())
                }
                .buttonStyle(.plain)
            }
            Text("RewordMe")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Spacer()
            if session.stage == .result, !session.modelLabel.isEmpty {
                Text(session.modelLabel)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                    .lineLimit(1)
            }
            Button {
                session.onClose?()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(.secondary)
                    .frame(width: 20, height: 20)
                    .background(.quaternary.opacity(0.5), in: Circle())
            }
            .buttonStyle(.plain)
        }
    }

    private var isFailed: Bool {
        if case .failed = session.stage { return true }
        return false
    }

    // MARK: - Stages

    @ViewBuilder
    private var stageContent: some View {
        if session.original.isEmpty {
            emptyHint
        } else {
            switch session.stage {
            case .menu: menu
            case .loading: loading
            case .result: resultView
            case .failed(let message): errorView(message)
            }
        }
    }

    private var emptyHint: some View {
        VStack(spacing: 8) {
            Image(systemName: "cursorarrow.and.square.on.square.dashed")
                .font(.system(size: 22))
                .foregroundStyle(.secondary)
            Text("No text selected. Select some text, then press \(ConfigStore().load().hotkey.display).")
                .font(.callout)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, minHeight: 90)
    }

    // MARK: Menu

    private var menu: some View {
        VStack(alignment: .leading, spacing: 10) {
            describeField

            HStack(spacing: 8) {
                bigButton(RewordSession.proofread) {
                    session.reword(instruction: RewordSession.proofread.instruction)
                }
                bigButton(RewordSession.rewrite) {
                    session.reword(instruction: nil)
                }
            }

            Divider()

            VStack(alignment: .leading, spacing: 2) {
                ForEach(RewordSession.tonePresets) { preset in
                    presetRow(preset)
                }
            }
        }
    }

    private var describeField: some View {
        HStack(spacing: 6) {
            Image(systemName: "sparkles")
                .font(.caption)
                .foregroundStyle(
                    LinearGradient(
                        colors: [.purple, .blue],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
            TextField("Describe your change", text: $session.steering)
                .textFieldStyle(.plain)
                .focused($steeringFocused)
                .onSubmit { session.reword() }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Capsule().fill(.background.opacity(0.4)))
        .overlay(
            Capsule().strokeBorder(
                intelligenceGradient,
                lineWidth: steeringFocused ? 1.8 : 1.1
            )
            .opacity(steeringFocused ? 1 : 0.7)
        )
        .animation(.easeOut(duration: 0.15), value: steeringFocused)
    }

    private func bigButton(_ preset: RewordSession.Preset, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 5) {
                Image(systemName: preset.icon)
                    .font(.system(size: 15, weight: .medium))
                Text(preset.title)
                    .font(.caption)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 9)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(.background.opacity(0.4))
            )
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func presetRow(_ preset: RewordSession.Preset) -> some View {
        Button {
            session.reword(instruction: preset.instruction)
        } label: {
            HStack(spacing: 9) {
                Image(systemName: preset.icon)
                    .font(.system(size: 12))
                    .frame(width: 18)
                    .foregroundStyle(.secondary)
                Text(preset.title)
                    .font(.body)
                Spacer()
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    // MARK: Loading

    private var loading: some View {
        VStack(spacing: 10) {
            ProgressView()
                .controlSize(.small)
            Text("Rewording...")
                .font(.callout)
                .foregroundStyle(.secondary)
            Button("Cancel") { session.backToMenu() }
                .glassButtonStyle()
                .controlSize(.small)
        }
        .frame(maxWidth: .infinity, minHeight: 110)
        .transition(.opacity)
    }

    // MARK: Result

    private var resultView: some View {
        VStack(alignment: .leading, spacing: 10) {
            ScrollView {
                Text(session.result)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
            }
            .frame(minHeight: 70, maxHeight: 200)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(.background.opacity(0.35))
            )

            describeField

            HStack(spacing: 8) {
                Button {
                    session.regenerate()
                } label: {
                    Label("Again", systemImage: "arrow.clockwise")
                }
                .glassButtonStyle()

                Spacer()

                Button("Copy") { session.copyResult() }
                    .glassButtonStyle()

                Button("Replace") { session.replaceSelection() }
                    .glassButtonStyle(prominent: true)
                    .keyboardShortcut(.defaultAction)
            }
        }
        .transition(.opacity.combined(with: .move(edge: .bottom)))
    }

    // MARK: Error

    private func errorView(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Something went wrong", systemImage: "exclamationmark.triangle.fill")
                .font(.callout.weight(.semibold))
                .foregroundStyle(.orange)
            Text(message)
                .font(.callout)
                .foregroundStyle(.secondary)
                .textSelection(.enabled)
            HStack {
                Spacer()
                Button("Try Again") { session.regenerate() }
                    .glassButtonStyle()
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(.background.opacity(0.35))
        )
        .transition(.opacity)
    }
}

// MARK: - Glass helpers

extension View {
    /// Glass button styles on macOS 26+, bordered fallbacks below.
    @ViewBuilder
    func glassButtonStyle(prominent: Bool = false) -> some View {
        if #available(macOS 26.0, *) {
            if prominent {
                buttonStyle(.glassProminent)
            } else {
                buttonStyle(.glass)
            }
        } else {
            if prominent {
                buttonStyle(.borderedProminent)
            } else {
                buttonStyle(.bordered)
            }
        }
    }
}
