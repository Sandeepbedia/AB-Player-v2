# Contributing to AB Player v2

Thanks for your interest in contributing! Please read this guide and the
[LICENSE](./LICENSE) before submitting any changes — contributions are
welcome, but redistribution/copying of the full project is not permitted
(see LICENSE Section 3).

## Ground rules

- All contributions happen via **Pull Requests** — direct pushes to
  `main` are not allowed.
- Forks should only be used to prepare a PR, not to host or publish
  your own copy of the app.
- By submitting a PR you agree to the contribution terms in the
  LICENSE (Section 4).

## How to contribute

1. **Fork** the repository and clone your fork:
   ```
   git clone https://github.com/<your-username>/AB-Player-v2.git
   cd AB-Player-v2
   ```
2. **Create a branch** for your change:
   ```
   git checkout -b fix/short-description
   ```
3. **Set up signing** (only needed for release builds — debug builds
   work without it). See [`SIGNING.md`](./SIGNING.md).
4. **Build & test locally**:
   ```
   ./gradlew installDebug
   ```
5. **Commit** with a clear message:
   ```
   git commit -m "fix: correct equalizer preset reset bug"
   ```
6. **Push and open a PR** against `main` of the original repo, with:
   - A clear title and description of what changed and why
   - Screenshots/screen recordings for UI changes
   - Steps to test

## Code style

- Kotlin + Jetpack Compose, following standard Kotlin coding
  conventions.
- Keep composables small and stateless where possible; hoist state up.
- Use existing package structure (`app/src/main/java/...`) — don't
  introduce a new architecture pattern without discussion first.
- Run `./gradlew ktlintCheck` (if configured) before pushing.

## Reporting bugs / requesting features

Use the **Issues** tab. Include:
- App version (Settings → About)
- Android version / device
- Steps to reproduce (for bugs)

## Code of conduct

Be respectful. No harassment, spam, or off-topic promotion in issues
or PRs.
