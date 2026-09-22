# Contributing to Baby Buddy Pocket

Bug fixes, documentation, testing, and useful ideas are welcome. This is an Android companion for an existing Baby Buddy server. Keep it responsive, easy to build, and reliable offline, with as few dependencies as practical.

## Start with the problem

- Search [existing issues](https://github.com/Felixre7/Baby-Buddy-Pocket/issues) before [reporting a bug](https://github.com/Felixre7/Baby-Buddy-Pocket/issues/new/choose).
- Propose features in [Discussions → Ideas](https://github.com/Felixre7/Baby-Buddy-Pocket/discussions/categories/ideas). Describe the problem and any simpler alternatives. Maintainers can turn accepted ideas into issues when they are ready for implementation.
- Use [Discussions → Q&A](https://github.com/Felixre7/Baby-Buddy-Pocket/discussions/categories/q-a) for setup and usage questions.
- Before implementing a feature or substantial fix, get agreement on the issue or discussion. Unsolicited large changes may not be reviewed. Small documentation corrections can go straight to a pull request.

Reports are public. Never include API tokens, setup QR codes, server addresses, children's details, or private activity records. For sync problems, explain whether the phone was offline and whether another caregiver was using the same child or timer. An optional diagnostic report is available under **Settings → Report a problem**; preview it before sharing. Crop or redact screenshots separately.

## AI usage policy

- AI tools may help with implementation. The contributor remains responsible for understanding, reviewing, and testing every change, including its licensing and privacy implications.
- Keep pull requests focused and small. Around 100 changed lines is a useful review target; agree on a larger change with a maintainer first. This is guidance, not an automated cutoff, and applies equally to work written with or without AI.
- Bug reports and feature proposals must describe your own experience in your own words. Do not submit generated reports or AI-written summaries as your problem statement. Real diagnostic output is welcome as supporting evidence.
- Write in the language you are comfortable using; English is not required. Maintainers can arrange translation when needed.
- Do not submit changes you cannot explain, claims you have not checked, or tests you have not run.

These workflow principles follow [LiftLog's contributing and AI policy](https://github.com/LiamMorrow/LiftLog/blob/main/CONTRIBUTING.md#ai-usage-policy). This guide and the forms use project-specific wording and requirements.

## Making a change

1. Fork the repository and create a branch for the agreed change.
2. Follow the [build instructions](README.md#build-it-yourself) and the existing Java/framework-view style. Avoid unrelated formatting, extra services, or dependencies without a clear benefit.
3. Preserve durable offline entries, explicit handling of uncertain uploads, and one running timer per child. Review [AGENTS.md](AGENTS.md) for the project conventions; its private Obsidian bookkeeping is handled by the maintainer, not expected from external contributors.
4. Run the checks relevant to your change. For app changes, the usual starting point is:

   ```sh
   sh ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
   ```

   On Windows, use `gradlew.bat` with the same tasks. For UI, storage, or sync changes, use the [emulator instructions](README.md#tests) with synthetic data. Record the actual device and Android/API version. Start with Pixel 9 Pro on the latest stable Android; follow with relevant older-device checks. Never run the destructive smoke fixture on a personal installation.

5. Open a pull request against `main`, link the agreed issue/discussion, and explain the resulting behavior, validation, and any remaining limitations. Use clear commit messages and update affected documentation. Add regression coverage where it protects meaningful behavior.

Documentation and issue-template changes do not need an APK build; check their links, formatting, and forms instead. External contributors do not need to produce a release or access the maintainer's Obsidian vault.

## Before requesting review

- The change solves the agreed problem without unrelated additions.
- Relevant build, test, lint, and device checks are reported accurately, including anything not run.
- The diff contains no build artifacts, signing keys, credentials, or private family data.
- Documentation matches the implemented behavior, and obsolete code has been removed where safe.

## License

Contributions are accepted under the [GNU General Public License, version 3 only](LICENSE) (`GPL-3.0-only`). Preserve third-party license notices and only contribute material you have permission to share under these terms.
