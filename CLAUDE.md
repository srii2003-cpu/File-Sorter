# Private Image Organiser (Android, V1)

On-device Android app that finds documents received through WhatsApp and Telegram and helps clear junk forwards. The V3 spec is the source of truth; any deviation from it is logged in [docs/decisions.md](docs/decisions.md) with date, decision and reason.

## Non-negotiables

1. **No network access.** The merged manifest must not contain `INTERNET` or `ACCESS_NETWORK_STATE`. Libraries that add them (e.g. ML Kit transport) are stripped with `tools:node="remove"`, and a build check fails if either appears in the merged manifest.
2. **No LLM, cloud APIs, analytics, crash-reporting SDKs or ads.**
3. **Never modify or move original files.** The only destructive action is `MediaStore.createTrashRequest()`, triggered by the user and confirmed in the system dialog.
4. **Never request** `MANAGE_EXTERNAL_STORAGE`, accessibility services or notification listeners. Never read WhatsApp/Telegram databases or `Android/data/`.
5. **Never log OCR text, file paths or detected ID numbers.** Logging is stripped from release builds.
6. **No real ID documents in test fixtures.** Specimen cards or fake-data mockups only.
7. **Uncertain = Review, never guessed.** Uncertain images never enter the junk queue.

Golden rule: junk *detection* is automatic; junk *deletion* is always user-controlled.

## Working rules

- Work in phases (0–4) and stop for review after each. Do not add features beyond the current phase.
- **Phase 2 does not start until the owner confirms the validation gate has passed.** Phases 0–1 are the evaluation build.
- Small commits, one concern each; tests alongside code; nothing is "done" with failing tests.
- If the spec is ambiguous, wrong or impossible on Android, stop and ask; do not improvise.
- Do not add a library without saying why.
- Every performance number comes from a measurement, with device and conditions stated.
- Gradle runs on Android Studio's bundled JDK 21 (not the system JDK 24).
