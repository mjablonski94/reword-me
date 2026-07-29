import RewordMeCore
import SwiftUI

struct PopupView: View {
    @ObservedObject var session: RewordSession
    @FocusState private var steeringFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            header
            content
                .animation(.spring(duration: 0.35), value: session.isLoading)
                .animation(.spring(duration: 0.35), value: session.result)
            steeringField
            actions
        }
        .padding(18)
        .frame(width: 460)
        .background(
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(.ultraThinMaterial)
        )
    }

    // MARK: - Header

    private var header: some View {
        HStack(spacing: 8) {
            Image(systemName: "wand.and.sparkles")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(
                    LinearGradient(
                        colors: [.purple, .indigo, .blue],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .symbolEffect(.pulse, isActive: session.isLoading)
            Text("RewordMe")
                .font(.headline)
            Spacer()
            if !session.modelLabel.isEmpty {
                Text(session.modelLabel)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                    .lineLimit(1)
            }
            Button {
                session.onClose?()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(.secondary)
                    .frame(width: 22, height: 22)
                    .background(.quaternary.opacity(0.5), in: Circle())
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        if session.original.isEmpty {
            hintCard(
                icon: "cursorarrow.and.square.on.square.dashed",
                text: "No text selected. Select some text, then press Option+Command+R."
            )
        } else if session.isLoading {
            VStack(spacing: 10) {
                ProgressView()
                    .controlSize(.small)
                Text("Rewording...")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, minHeight: 110)
            .transition(.opacity)
        } else if let error = session.errorMessage {
            VStack(alignment: .leading, spacing: 6) {
                Label("Something went wrong", systemImage: "exclamationmark.triangle.fill")
                    .font(.callout.weight(.semibold))
                    .foregroundStyle(.orange)
                Text(error)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            }
            .padding(12)
            .frame(maxWidth: .infinity, minHeight: 90, alignment: .topLeading)
            .background(inset)
            .transition(.opacity.combined(with: .move(edge: .bottom)))
        } else {
            ScrollView {
                Text(session.result)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
            }
            .frame(minHeight: 90, maxHeight: 230)
            .background(inset)
            .transition(.opacity.combined(with: .move(edge: .bottom)))
        }
    }

    private func hintCard(icon: String, text: String) -> some View {
        VStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 26))
                .foregroundStyle(.secondary)
            Text(text)
                .font(.callout)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, minHeight: 110)
    }

    /// Recessed rounded well the result text sits in, so it reads as a
    /// layer beneath the sheet. No stroke - contrast alone separates it.
    private var inset: some View {
        RoundedRectangle(cornerRadius: 14, style: .continuous)
            .fill(.background.opacity(0.35))
    }

    // MARK: - Steering

    private var steeringField: some View {
        HStack(spacing: 6) {
            Image(systemName: "slider.horizontal.3")
                .font(.caption)
                .foregroundStyle(.secondary)
            TextField(
                "Steer the next generation, e.g. \"more formal\"",
                text: $session.steering
            )
            .textFieldStyle(.plain)
            .focused($steeringFocused)
            .onSubmit { session.generate() }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            Capsule().fill(.background.opacity(steeringFocused ? 0.55 : 0.35))
        )
        .overlay {
            // Focus feedback only - no resting outline.
            if steeringFocused {
                Capsule().strokeBorder(.tint, lineWidth: 1.5)
            }
        }
        .animation(.easeOut(duration: 0.15), value: steeringFocused)
        .disabled(session.original.isEmpty)
    }

    // MARK: - Actions

    private var actions: some View {
        HStack(spacing: 8) {
            Button {
                session.generate()
            } label: {
                Label("Regenerate", systemImage: "arrow.clockwise")
            }
            .glassButtonStyle()
            .disabled(session.isLoading || session.original.isEmpty)

            Spacer()

            Button("Copy") {
                session.copyResult()
            }
            .glassButtonStyle()
            .disabled(session.result.isEmpty || session.isLoading)

            Button("Replace") {
                session.replaceSelection()
            }
            .glassButtonStyle(prominent: true)
            .keyboardShortcut(.defaultAction)
            .disabled(session.result.isEmpty || session.isLoading)
        }
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
