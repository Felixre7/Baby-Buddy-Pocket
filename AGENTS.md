# Baby Buddy Pocket project workflow

The public app name is **Baby Buddy Pocket**. The Android identity is `com.babybuddypocket.app`. Version 0.1.10 deliberately starts a fresh installation, as authorized by the user; retain this new identity for future updates. Keep the existing-server requirement prominent in onboarding and store descriptions. Keep the former prototype name out of all committed text, identifiers, and paths.

## Product priorities

- The user's explicit priority is useful functionality and simplicity over almost everything. Optimize for maintainability, responsiveness, easy self-builds, and few dependencies rather than feature count.
- Challenge suggestions that introduce disproportionate complexity, extra services, special cases, or maintenance. Explain the tradeoff briefly and suggest a simpler, faster option before implementing substantial additions. Do not turn every routine decision into an approval request.
- Reliable offline logging is essential. Start and stop actions must persist locally and recover after restart; exact cross-device timing is secondary. Shared timers must not sacrifice durable offline logs or silently retry ambiguous writes.
- Allow one running timer per child across all activities. Check known timers and recheck before publishing; preserve offline conflicts for an explicit user choice. Different children and manual logs remain independent. Never claim client-only checks guarantee atomic cross-device starts or finishes.
- Use one activity editor for historical and pending records. Persist offline edits, target the existing record ID, and send only changed fields after checking current server values. Preserve conflicting or uncertain work for review; never fall back to creating a new activity when an edited record was removed. Per-activity sync symbols should stay discreet and open the affected entry directly.
- Prefer existing platform capabilities and shared code. Add abstractions or dependencies only when they make the implementation meaningfully simpler or support necessary functionality. Keep correctness and data integrity intact.
- Remove code, resources, flags, tests, and temporary helpers made obsolete by a change instead of leaving dormant paths. Check indirect callers, Android callbacks, queued/offline paths, and upgrade requirements before declaring code unused. Keep necessary migration cleanup and meaningful regression coverage.
- The demo is local synthetic practice data only. The public demo was removed at the user's request because its login, network, cache, and mode logic added complexity without enough benefit. Do not reintroduce it by default.

## Testing and APK delivery

- For ordinary updates, features, and bug fixes, concentrate initial emulator testing on **Pixel 9 Pro with the latest stable Android image available**. State the actual Android/API version tested; do not imply a newer version was tested merely because the phone profile matches.
- Run focused unit/build checks and the relevant Pixel 9 Pro checks, then package and deliver the APK for physical-phone testing promptly. Do not hold the first test APK for the full compatibility matrix.
- Continue older-Android and other device/layout tests in parallel after the APK is available. Report their results separately; if they uncover an app bug, fix it and deliver a new identifiable build.
- Run broader checks when the change warrants them, rather than repeating the entire device matrix for every small change. Keep the Obsidian changelog clear about which checks were complete when an APK was first delivered and which finished afterward.

## Changelog maintenance

Keep proposals, brainstorming, idea generation, and research into undecided options in the Obsidian project, not in Git. Repository documentation should describe implemented behavior, verified results, and established build/release instructions. When discussing possible work, record it in Obsidian without creating or committing proposal documents in the repository.

The user requested that the Obsidian changelog stay up to date whenever we make project changes.

- Main note: the Obsidian project note with alias **Baby Buddy Pocket - Android app**.
- Changelog: follow the changelog link in that main note (alias **Baby Buddy Pocket - Changelog**).
- For every completed batch of app, build, test, documentation, or design changes, update the changelog in the same task before reporting completion. Record the date, user-visible changes, relevant design decisions, actual validation, and material limitations. Documentation-only changes also belong in the log; do not invent a release or rebuild an APK for them.
- Use newest-first entries. Put work that has not shipped under Unreleased; move it to a versioned entry when an APK is delivered, including version name/code and artifact paths. Preserve prior release history and distinguish checks on earlier builds from checks on the final delivered build.
- Read both notes fresh before editing. Preserve user edits, the main note's important design choices at the top, and its Obsidian link to the changelog. Keep current version/artifact information in the main note consistent with delivered releases.
- Never include private API keys, session cookies, or family record contents. Use existing verification reports as evidence; do not describe unrun tests as passed.
- If the vault is unavailable or a write is blocked, stage the proposed entry in this workspace and report the unsynced note update rather than claiming success.
