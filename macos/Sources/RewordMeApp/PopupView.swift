import SwiftUI

/// Writing-Tools-style panel: a describe field with an intelligence glow,
/// two big actions, tone presets - and the result view after generating.
struct PopupView: View {
    @ObservedObject var viewModel: RewordViewModel
    @FocusState private var steeringFocused: Bool

    private let intelligenceGradient = AngularGradient(
        colors: [.blue, .purple, .pink, .orange, .blue],
        center: .center
    )

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            header
            stageContent
                .animation(.spring(duration: 0.3), value: viewModel.stage)
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
            if viewModel.stage == .result || isFailed {
                Button {
                    viewModel.backToMenu()
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
            if viewModel.stage == .result, !viewModel.modelLabel.isEmpty {
                Text(viewModel.modelLabel)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                    .lineLimit(1)
            }
            Button {
                viewModel.onClose?()
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
        if case .failed = viewModel.stage { return true }
        return false
    }

    // MARK: - Stages

    @ViewBuilder
    private var stageContent: some View {
        if viewModel.original.isEmpty {
            emptyHint
        } else {
            switch viewModel.stage {
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
            Text(Loc.noSelection(viewModel.hotkeyDisplay))
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
                bigButton(RewordViewModel.proofread) {
                    viewModel.reword(instruction: RewordViewModel.proofread.instruction)
                }
                bigButton(RewordViewModel.rewrite) {
                    viewModel.reword(instruction: nil)
                }
            }

            Divider()

            VStack(alignment: .leading, spacing: 2) {
                ForEach(RewordViewModel.tonePresets) { preset in
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
            TextField(Loc.describePlaceholder, text: $viewModel.steering)
                .textFieldStyle(.plain)
                .focused($steeringFocused)
                .onSubmit { viewModel.reword() }
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

    private func bigButton(_ preset: RewordViewModel.Preset, action: @escaping () -> Void) -> some View {
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

    private func presetRow(_ preset: RewordViewModel.Preset) -> some View {
        Button {
            viewModel.reword(instruction: preset.instruction)
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
            Text(Loc.rewording)
                .font(.callout)
                .foregroundStyle(.secondary)
            Button(Loc.cancel) { viewModel.backToMenu() }
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
                Text(viewModel.result)
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
                    viewModel.regenerate()
                } label: {
                    Label(Loc.again, systemImage: "arrow.clockwise")
                }
                .glassButtonStyle()

                Spacer()

                Button(Loc.copy) { viewModel.copyResult() }
                    .glassButtonStyle()

                Button(Loc.replace) { viewModel.replaceSelection() }
                    .glassButtonStyle(prominent: true)
                    .keyboardShortcut(.defaultAction)
            }
        }
        .transition(.opacity.combined(with: .move(edge: .bottom)))
    }

    // MARK: Error

    private func errorView(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(Loc.errorTitle, systemImage: "exclamationmark.triangle.fill")
                .font(.callout.weight(.semibold))
                .foregroundStyle(.orange)
            Text(message)
                .font(.callout)
                .foregroundStyle(.secondary)
                .textSelection(.enabled)
            HStack {
                Spacer()
                Button(Loc.tryAgain) { viewModel.regenerate() }
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
