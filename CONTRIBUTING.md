# Contributing to Mensis

Thanks for your interest. A few rules keep the app trustworthy:

1. **Privacy first.** No analytics, no network code outside `:backup-drive`, no health data in logs, no new
   permissions without discussion.
2. **Synthetic data only.** Never commit real cycle data, screenshots of real data, or real account
   details. Test fixtures are generated.
3. **Every dependency is justified.** A pull request that adds a library explains why in its description
   and commit message, names the licence, and confirms it makes no network calls on its own.
4. **Derived, never stored.** Cycles, phases and predictions are computed from daily logs and settings.
   Thresholds live in `PredictionConstants.kt` with a comment a biologist can review.
5. **Copy and layout** follow the app's design spec; keep user-facing wording consistent with existing screens.
6. **Checks must pass**: `./gradlew check` (ktlint, detekt, lint, unit tests) and, for data or UI
   changes, `./gradlew connectedCheck` on a device or emulator.

## Workflow

- Fork, branch from `main`, open a pull request. CI runs ktlint, detekt, Android Lint, unit tests and a
  release build; the emulator workflow runs instrumented tests on `main`.
- One-line commit messages in Conventional Commits form: `feat:`, `fix:`, `chore:`, `docs:`, `build:`,
  `test:`, `refactor:`.
- Report security issues privately via the GitHub security advisory form rather than a public issue.
- Releases are made by the maintainer (`RELEASING.md`); pull requests do not change version numbers or
  store texts unless asked to.
