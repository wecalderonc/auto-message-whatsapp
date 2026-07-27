# WhatsApp Scheduler (sideload Android MVP)

Personal Android app that schedules one-to-one WhatsApp text messages and sends them by driving the installed WhatsApp UI with an AccessibilityService.

**Not for Google Play. Not an official WhatsApp integration.** Unauthorized automated messaging can violate [WhatsApp’s terms](https://faq.whatsapp.com/5957850900902049/) and risk account action. Prefer numbers you control, on your own device.

## Dev setup (verified on this machine)

Checked on 2026-07-27 for `/Users/will/auto-message-whatsapp`:

| Requirement | Status |
| --- | --- |
| JDK 17+ | **Installed** via Homebrew (`openjdk@17`) |
| Android SDK | **Installed** via `android-commandlinetools` at `/opt/homebrew/share/android-commandlinetools` (platform 35, build-tools, platform-tools) |
| Android Studio IDE | Optional — not required for CLI builds |
| `adb` | Available under the SDK `platform-tools` |
| Physical device + WhatsApp | **Not attached** during implementation — E2E Accessibility send not validated here |
| Unit tests + debug APK | **Passing** — `./gradlew :app:testDebugUnitTest :app:assembleDebug` |

### Build / install

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

# local.properties should point at ANDROID_HOME (see local.properties.example)
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
adb devices   # need a physical phone for WhatsApp E2E
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Emulator vs physical device

| Layer | Emulator | Physical device |
| --- | --- | --- |
| Compose UI / Room / schedule list | Yes | Yes |
| Exact alarms / permissions UI | Partial | Yes |
| Open WhatsApp + Accessibility Send | No | Yes |
| Lock-screen wait / unlock resume | Limited | Yes |

Never treat an emulator as proof that auto-send works.

## What the app does

1. You pick a **contact** (from device contacts / frequent list / phone number) or a **WhatsApp group** (exact group name).
2. You enter the message and schedule time — use quick chips **1 min**, **5 min**, **1 hour**, or **12 hours**, or a custom datetime.
3. Frequent contacts and group names are saved and ranked by how often you use them.
4. Local phone numbers (e.g. Colombian `3007162262`) are prefixed with your **selected country** calling code (default **Colombia +57** → `573007162262`) so WhatsApp does not misread them as another country. You can change the country on the home screen or when scheduling.
4. An exact alarm fires at the due time.
5. If the keyguard is locked, the app wakes the screen, posts an **Unlock to send** notification, waits up to ~3 minutes, then fails if still locked.
6. If unlocked and Accessibility is enabled:
   - **Contact:** opens WhatsApp via `api.whatsapp.com/send` and taps Send
   - **Group:** opens WhatsApp, searches the exact group title, opens the chat, types the message, taps Send
7. Result is persisted (`SENT` / `FAILED`) and notified.

Silent send behind PIN/biometric lock is **not possible** with UI automation. Official bulk/auto send requires the WhatsApp Business Platform.

## First-run setup on the phone

1. Sideload the debug APK.
2. Accept the in-app risk disclosure.
3. Grant notifications when prompted.
4. Tap **Accessibility** in the setup card → enable **WhatsApp Scheduler**.
5. Tap **Alarms** if exact alarms are denied (Android 12+).
6. Optionally allow unrestricted battery / disable OEM battery killing.
7. When scheduling to a contact, allow **Contacts** access (or type the number).
8. For groups, type the **exact** WhatsApp group title (case-insensitive match preferred).
9. Keep the phone unlocked (or unlock promptly) when a message is due.

## Manual test checklist

- [ ] Create a schedule ≥15s ahead; status `PENDING`
- [ ] Unlocked device: message sends once; status `SENT`
- [ ] Locked device: unlock notification → unlock in time → `SENT`
- [ ] Locked past wait window → `FAILED` with unlock timeout reason
- [ ] Reboot with a future pending schedule → alarm restored
- [ ] Disable Accessibility → due send fails with clear reason
- [ ] Uninstall WhatsApp → fails with “not installed”
- [ ] Cancel a pending item → `CANCELLED`, alarm removed
- [ ] Duplicate alarm delivery does not double-send (attempt token)

## Project layout

- `app/src/main/java/.../data` — Room entity, DAO, repository, statuses
- `app/src/main/java/.../scheduling` — AlarmManager, boot restore, send coordinator
- `app/src/main/java/.../automation` — AccessibilityService state machine
- `app/src/main/java/.../ui` — Compose screens + ViewModel
- `app/src/test` — phone normalization + repository status tests

## Risks and limits

- WhatsApp UI/resource IDs change; selectors may break after updates.
- OEM battery savers can kill alarms/services.
- Google Play [restricts Accessibility automation](https://support.google.com/googleplay/android-developer/answer/10964491); this app is personal/sideload only.
- Exact alarms: see [Android alarm guidance](https://developer.android.com/develop/background-work/services/alarms).

## License / use

Provided as a personal experiment. You are responsible for complying with WhatsApp’s terms, local law, and recipient consent.
