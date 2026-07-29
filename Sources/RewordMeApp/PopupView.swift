import RewordMeCore
import SwiftUI

struct PopupView: View {
    @ObservedObject var session: RewordSession

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            header
            content
            steeringField
            actions
        }
        .padding(14)
        .frame(width: 440)
        .background(.regularMaterial)
    }

    private var header: some View {
        HStack {
            Label("RewordMe", systemImage: "arrow.trianglehead.2.clockwise.rotate.90")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Spacer()
            if !session.modelLabel.isEmpty {
                Text(session.modelLabel)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                    .lineLimit(1)
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if session.original.isEmpty {
            Text("No text selected. Select some text, then press Option+Command+R.")
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, minHeight: 60, alignment: .center)
        } else if session.isLoading {
            HStack(spacing: 8) {
                ProgressView()
                    .controlSize(.small)
                Text("Rewording...")
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, minHeight: 100, alignment: .center)
        } else if let error = session.errorMessage {
            VStack(alignment: .leading, spacing: 6) {
                Label("Something went wrong", systemImage: "exclamationmark.triangle")
                    .font(.callout.weight(.semibold))
                Text(error)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            }
            .frame(maxWidth: .infinity, minHeight: 100, alignment: .topLeading)
        } else {
            ScrollView {
                Text(session.result)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(minHeight: 80, maxHeight: 220)
        }
    }

    private var steeringField: some View {
        TextField(
            "Steer the next generation, e.g. \"more formal\"",
            text: $session.steering
        )
        .textFieldStyle(.roundedBorder)
        .onSubmit { session.generate() }
        .disabled(session.original.isEmpty)
    }

    private var actions: some View {
        HStack {
            Button {
                session.generate()
            } label: {
                Label("Regenerate", systemImage: "arrow.clockwise")
            }
            .disabled(session.isLoading || session.original.isEmpty)

            Spacer()

            Button("Copy") {
                session.copyResult()
            }
            .disabled(session.result.isEmpty || session.isLoading)

            Button("Replace") {
                session.replaceSelection()
            }
            .keyboardShortcut(.defaultAction)
            .disabled(session.result.isEmpty || session.isLoading)
        }
    }
}
