# WhatsApp Scheduler (sideload Android MVP)

Android app that schedules WhatsApp text messages (contacts or groups) by driving the installed WhatsApp UI with an AccessibilityService.

**Not for Google Play. Not an official WhatsApp integration.** Unauthorized automated messaging can violate [WhatsApp’s terms](https://faq.whatsapp.com/5957850900902049/) and risk account action. Prefer numbers you control, on your own device.

## Dev setup

### Requirements

| Requirement | Notes |
| --- | --- |
| JDK 17+ | Required to build with Gradle |
| Android SDK | Platform 35, Build-Tools, Platform-Tools (`adb`) |
| Android Studio | Optional; CLI builds work with the SDK alone |
| Physical Android phone | Needed for real WhatsApp Accessibility E2E tests |
| WhatsApp installed | Consumer or Business app, logged in |

Copy `local.properties.example` to `local.properties` and set `sdk.dir` to your Android SDK path.

### Build / install

```bash
# Point JAVA_HOME / ANDROID_HOME at your local installs, then:
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
adb devices
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
4. Local phone numbers (e.g. `3105551234`) are prefixed with your **selected country** calling code (default **Colombia +57** → `573105551234`) so WhatsApp does not misread them as another country. You can change the country on the home screen or when scheduling.
5. An exact alarm fires at the due time.
6. If the keyguard is locked, the app wakes the screen, posts an **Unlock to send** notification, waits up to **1 hour**, then fails if still locked.
7. If unlocked and Accessibility is enabled:
   - **Contact:** opens WhatsApp via `api.whatsapp.com/send` and taps Send
   - **Group:** opens WhatsApp, searches the exact group title, opens the chat, types the message, taps Send
8. Result is persisted (`SENT` / `FAILED`) and notified.

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
- **Banking / secure apps:** many bank apps warn or refuse to open while *any* Accessibility service is enabled. Turn off Accessibility for WhatsApp Scheduler before using your bank, then re-enable it when you need scheduled sends. There is no reliable way to keep Accessibility on and still satisfy those bank checks.
- Exact alarms: see [Android alarm guidance](https://developer.android.com/develop/background-work/services/alarms).

## License / use

Provided as a personal experiment. You are responsible for complying with WhatsApp’s terms, local law, and recipient consent.
