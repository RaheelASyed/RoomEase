# RoomEase

RoomEase is a native Android app (Kotlin, Jetpack Compose, Material 3) that helps roommates share chores, split expenses (with on-device receipt scanning), and message each other. It is my CS 4720 final project.

**Stack:** Kotlin · Jetpack Compose + Material 3 · Navigation Compose (single Activity) · ViewModel + StateFlow + SavedStateHandle · Firebase Auth (email/password + Google through Credential Manager) · Cloud Firestore · Preferences DataStore · CameraX · ML Kit on-device text recognition · Android SpeechRecognizer.

The design follows the Figma *Mockups v2 (Android)* page: brand green `#1E7D5A` / blue `#1D72A2`, a light grey canvas, white rounded cards, initials avatars and pill buttons.

---

## 1. Setup

### Requirements
- Android Studio (2026.x) with Android SDK 37. The project targets SDK 37 and requires at least SDK 26.
- A Firebase project on the free **Spark** plan. No billing, Cloud Functions or Cloud Storage are used.

### Firebase console (one time)
1. **Register the Android app** with package `edu.virginia.cs4720.roomease`.
2. **Authentication → Sign-in method:**
   - Enable **Email/Password**.
   - Enable **Google** and set a support email.
3. **Add your signing fingerprint.** Google sign-in needs it.
   - Print the debug key's SHA-1:
     ```bash
     ./gradlew signingReport
     ```
     The same value is shown by:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
   - In **Project settings → Your apps → Add fingerprint**, paste the SHA-1 (and the SHA-256).
   - Add every machine's debug key and any release key the same way.
4. **Download `google-services.json` again** after enabling Google sign-in, and put it in `app/`.
   - The new file contains the OAuth web client ID that the build turns into `R.string.default_web_client_id`.
   - The app looks this ID up at runtime. Until it exists, "Continue with Google" shows a "not set up yet" message instead of crashing.
   - `google-services.json` is git-ignored because this repo is public.
5. **Firestore Database:** create it in *production mode*.
6. **Publish the security rules**, either:
   - by pasting `firestore.rules` into **Firestore → Rules → Publish**, or
   - from the CLI:
     ```bash
     npx firebase-tools deploy --only firestore:rules,firestore:indexes --project <your-project-id>
     ```
   - ⚠️ Until the rules are published, production mode denies every request and the app can't load anything.

### Run
Open the project in Android Studio and press **Run**, or:
```bash
./gradlew installDebug
```

### Run against the local Firebase Emulator Suite (no live data)
```bash
cd firestore-tests && npm install && cd ..
```
```bash
./firestore-tests/node_modules/.bin/firebase emulators:start --only auth,firestore --project <your-project-id>
```
```bash
./gradlew installDebug -Proomease.useEmulator=true
```
```bash
adb reverse tcp:9099 tcp:9099 && adb reverse tcp:8080 tcp:8080
```
Debug builds with `-Proomease.useEmulator=true` connect to `127.0.0.1:9099` (Auth) and `127.0.0.1:8080` (Firestore). `adb reverse` forwards those ports from the device to your computer.

We don't use `10.0.2.2` because Android 17 (API 37) blocks apps from reaching local-network addresses without an extra permission. Loopback through `adb reverse` works on both emulators and USB-connected phones. Plain-HTTP (cleartext) access to the emulator is allowed **only in debug builds** (`app/src/debug/res/xml/network_security_config.xml`).

---

## 2. Architecture (and where each lecture shows up)

```
UI (Compose screens, stateless + wrapper "Route" composables)        L4, L6, L8
  └─ ViewModel per screen: one immutable UiState in a StateFlow,      L6, L9
     SavedStateHandle for restorable form values
      └─ Repositories (Auth, Household, Chore, Expense, Chat, Settings) L9
          └─ Firestore snapshot listeners → Flow (callbackFlow)        L12
             DataStore (settings, chat drafts)                         L9
             App-private files (receipt photos)                        L17
```
- **Wiring:** dependencies are wired by hand in `AppContainer`, which `RoomEaseApp` builds once (L9 "Wiring it up"). There's no Hilt.
- **Navigation:** a single Activity with Navigation Compose and string routes (L7 Model B).
  - Session state picks the graph: Login → Onboarding → Main. This is L11 "auth state drives navigation".
  - The main graph has four tabs: **Chores, Expenses, Chat, Settings**.
- **Data flow:** one direction only. State flows down; events flow up as lambdas (L6). Every screen has a `@Preview`, including one at `fontScale = 1.5f` (L8).
- **Layout of the code:**
  - `data/`: repositories and Firestore mapping
  - `domain/`: pure Kotlin (split math, receipt parser, validation), covered by unit tests
  - `ui/`: screens and ViewModels

### Design decisions that differ from the mockups
- **Tabs:** the Figma bottom bar is Home/Social/Money/Chores/Docs. The project requires four distinct signed-in screens, so the app uses **Chores / Expenses / Chat / Settings** in the same visual style.
- **No photos or file uploads:** Cloud Storage costs money, so the app uses initials avatars, and receipt photos never leave the phone.
- **No chore-swap feature:** there was no existing chore-swap implementation to keep, so none was added.

---

## 3. Firestore data model, queries and permissions

Everything is denied by default. Every collection below has explicit rules in `firestore.rules`, and the tests in `firestore-tests/` cover them.

| Collection | Fields | Queries the app runs | Who can do what |
|---|---|---|---|
| `users/{uid}` | `uid, displayName, email, householdId, pendingHouseholdId, createdAt` | Listen to own doc | Only the owner can read or write it. `householdId` is **navigation state only**. It can change once, from `null` to a household where the account is a member *after the same batch*. Access to household data is always checked against `members/{uid}`. |
| `inviteCodes/{code}` | `householdId, householdName, ownerId, createdAt` | `get` by typed code | Any signed-in user can `get` one code; listing is denied. The owner creates it in the household-creation batch. It can never be overwritten. |
| `households/{hid}` | `name, ownerId, inviteCode, createdAt` | Listen to doc | Members can read. It's created atomically with the owner's member doc and its invite code. Only the owner can rename it; `ownerId` is immutable. |
| `households/{hid}/members/{uid}` | `uid, displayName, role (owner\|member), joinedAt` | Listen to own doc (routing); list the roster | The **membership authority**. You can always `get` your own doc; members can list the roster. Create is allowed **only** for the founding owner in the creation batch, or for a joiner whose `joinRequests/{uid}` the owner has **approved**. Either way the account must have had no household before. No updates or deletes. |
| `households/{hid}/joinRequests/{uid}` | `uid, displayName, email, code, status, createdAt, decidedBy, decidedAt` | Owner: `where status == pending`; joiner: listen to own doc | The joiner creates their own request with `status: pending` and the household's real code. Only the owner can change the status (once) to `approved` or `declined`. The joiner can withdraw. |
| `households/{hid}/chores/{id}` | `title, assigneeId, assigneeName, createdBy, createdByName, dueDate, done, completedAt, createdAt` | `orderBy dueDate` (live) | Members read. A member creates with `createdBy` = themselves and an assignee who is a member. **Only the assignee** can change `done`/`completedAt`; every other field is immutable. The creator or owner can delete. |
| `households/{hid}/expenses/{id}` | `description, merchant, totalCents (int), paidBy, paidByName, participantIds (1–8 members, unique), hasReceipt, createdAt` | `orderBy createdAt desc limit 200` (live) | Members read. The payer must be the writer, every participant must be a current member, the amount is in integer cents and greater than 0, and only the listed fields are allowed (so no image bytes). Expenses are append-only; the payer can delete. |
| `households/{hid}/messages/{id}` | `text (1–1000), senderId, senderName, createdAt` | `orderBy createdAt limitToLast 200` (live) | Members read. `senderId` must equal the writer (no forged senders), and `createdAt` must be the server time. No edits or deletes. |

**Indexes:** every query uses a single field, so `firestore.indexes.json` is empty and kept for deploys.

### Onboarding without Cloud Functions (owner-approved invites)
1. **Owner creates the household.** One client batch writes the household, the owner's member doc, `inviteCodes/{code}`, and `users/{uid}.householdId`. The rules check each piece against the others (`getAfter` / `existsAfter`).
2. **Joiner enters the code.** The app reads `inviteCodes/{code}` and creates `joinRequests/{joinerUid}`.
3. **Owner approves** in **Settings → Join requests**. The request's document ID is the joiner's UID, so the approval is tied to that one account.
4. **Joiner's app finishes the join.** It sees the approval and writes `members/{uid}` + `users/{uid}.householdId` in one batch. The rules allow that only when the request is `approved`.

Guessing a household ID, or even knowing the code, never grants membership.

### Simplifications made to stay enforceable on Spark
- **Shares aren't stored.** Each person's share is derived from `totalCents` + `participantIds` with a deterministic split: `total / n` each, and the leftover cents go one each to participants in sorted-UID order. Rules can't loop over a map to check a sum, so storing only these two fields means the stored data can't be internally inconsistent.
- **At most 8 participants per expense** (and the app caps households at 8). Rules check each participant's membership individually, and Firestore limits how many documents one write's rules can look up.
- **No leave/remove member, no editing chores or expenses after creation.** This keeps "one household per account" and ownership immutable without server code.

---

## 4. Lifecycle, offline and duplicate protection

- **What survives rotation and process death:**
  - The current tab and screen (saved NavController back stack)
  - Chore filter, expanded expense, every form field, the chat draft, the receipt preview, and the scan result (`SavedStateHandle`)
  - Passwords are kept in ViewModel memory only, never written to saved state.
- **What survives a full app restart:** the chat draft (DataStore) and the receipt photo (`filesDir/receipts/pending_<expenseId>.jpg`).
- **Camera:** bound to the screen lifecycle with `bindToLifecycle`, so it's released on stop (incoming call, home button) and unbound when you leave the screen.
- **Microphone:** `SpeechRecognizer` is cancelled and destroyed on `ON_PAUSE` and on leaving the screen.
- **No duplicate saves:** new chores and expenses get a document ID once, stored in saved state.
  - A retry or rotation reuses the same ID.
  - The app checks the local cache before writing.
  - Buttons are disabled while saving.
- **No duplicate sends:** chat clears the draft before queuing the write, and Firestore's offline queue retries delivery on its own.
- **Pending sync:** listeners use `MetadataChanges.INCLUDE`.
  - Unconfirmed writes show **"Pending sync"** (chores, expenses) or **"Sending…"** (chat).
  - If the server rejects a queued write, a snackbar explains why.
- **Receipt privacy:** photos are excluded from cloud backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`). Only the confirmed merchant and total reach Firestore.

---

## 5. Rubric-to-feature checklist

| Requirement | Where |
|---|---|
| Login screen on a fresh launch; email/password sign-in, account creation, sign-out | `ui/auth/*`, `RoomEaseRoot`, Settings → Sign out |
| Google sign-in (Credential Manager + Firebase Auth) | `ui/auth/GoogleSignIn.kt`, `AuthRepository.signInWithGoogle` |
| Returning users skip login | `SessionViewModel` (Firebase restores the session) |
| Create household / join through an owner-approved invite, one household per account | `ui/onboarding/*`, `HouseholdRepository`, rules |
| Roster and data private to current members | `firestore.rules` + rule tests |
| Chores: create, assign, due date, assignee completes, live for the creator | `ui/chores/*`, `ChoreRepository` |
| Expenses: select roommates, split, ledger, integer cents, deterministic leftover | `ui/expenses/*`, `domain/ExpenseSplit.kt` |
| Scan receipt: in-app CameraX, capture/preview/retake/review, on-device OCR, editable, manual fallback, photo stays on the device | `ui/expenses/ReceiptCamera.kt`, `ReceiptTextRecognizer`, `domain/ReceiptParser.kt` |
| Chat: live, sender shown, pending vs synced, saved drafts | `ui/chat/*`, `ChatRepository` |
| Mic dictation into an editable composer, permission denial and error handling | `ui/chat/SpeechToText.kt`, `ChatRoute` |
| Settings in DataStore: theme mode, default chore view, default split; each visibly applied and kept across relaunch | `data/SettingsRepository.kt`, `MainActivity`, `ChoresViewModel`, `AddExpenseViewModel` |
| Loading / empty / error / success states; 48dp touch targets; content descriptions; `sp` text | All screens (`ui/components/Components.kt`) |
| Theme.kt customized with brand colors and type (L8) | `ui/theme/*` |
| `firestore.rules`, `firebase.json`, indexes committed; deny by default | repo root |

### XP tracking (show to instructor; keep the Show & Tell proposal in sync)
| Feature | XP | Status |
|---|---|---|
| Camera (CameraX receipt scan) | 25 | Implemented |
| Messaging (Firestore household chat) | 25 | Implemented |
| DataStore settings (3 settings) | 15 | Implemented |
| Google sign-in | 10 | Implemented; needs SHA-1 + Google provider in the console |
| **Baseline total** | **75** | |
| Speech-to-Text (dictation for the microphone category) | 25 | **Conditional: pending instructor approval.** Not counted until approved. |

---

## 6. Demo checklist (Account A, Account B in the same household, Account C as an outsider)

For the full step-by-step emulator test plan (about 70 checks with expected results), see [docs/MANUAL_TEST_PLAN.md](docs/MANUAL_TEST_PLAN.md).

Use two phones or emulators, or sign out and back in on one.

- [ ] **Fresh launch:** clear app data → Login screen.
- [ ] **A creates an account** → Onboarding → creates "Maple House" → lands on Chores. Settings shows the invite code.
- [ ] **B creates an account** → Join household → enters the code → "Waiting for approval".
- [ ] **A approves B:** Settings → Join requests → Approve. B's screen joins on its own.
- [ ] **C** enters the same code but is declined (or never approved). C can't see any household data.
- [ ] **Chore loop:** A taps **New chore** → "Take out trash", assign to **B**, pick a due date → Save. A's checkbox is disabled.
  - B sees it under **My chores** and checks it.
  - **A's screen updates to "Completed today" without refreshing.**
- [ ] **Two-way chat:** A sends → B sees it with A's name; B replies → A sees it.
  - Turn on airplane mode, send → "Sending…"; turn it back off → shows a time.
- [ ] **Draft:** type a message, rotate the phone, then force-stop and reopen. The draft is still there.
- [ ] **Dictation:**
  - Tap the mic → **Don't allow** → you can still type.
  - Allow → speak → the words land in the composer for review → Send.
- [ ] **Receipt:** Expenses → Add expense → Scan receipt.
  - Allow the camera → capture → Retake → capture → Use photo.
  - The merchant and total are pre-filled (or a "couldn't read" message appears) and both stay editable.
  - Split between A and B → Save.
  - The ledger shows B owing half. The expanded row shows the photo with a "stays on this device" note. On B's phone the photo is absent.
- [ ] **Interruption:** while the scan review is open, press Home (or take a call) and come back. The preview is still there and the camera was released.
- [ ] **Settings:**
  - Dark → the whole app turns dark.
  - Default chore view: All → the Chores tab opens on All.
  - Default split: Choose manually → a new expense starts with only you selected.
  - Force-stop and reopen → all three are kept.
- [ ] **Google sign-in:** sign out → Continue with Google → pick an account → lands on Onboarding (new user) or Chores (existing member).

---

## 7. Commands and checks actually run (Nov 18, 2026)

| Command | Result |
|---|---|
| `./gradlew assembleDebug` | ✅ builds |
| `./gradlew :app:testDebugUnitTest` | ✅ 16 tests, 0 failures (split and ledger math, money parsing, receipt parser, validation, invite codes) |
| `./gradlew lintDebug` | ✅ 0 errors. Remaining warnings are only "newer AGP/Gradle/Compose BOM available". I didn't upgrade the toolchain. |
| `cd firestore-tests && npm test` | ✅ **28 rule tests pass** on the Firestore emulator: unauthenticated users, signed-in outsiders, self-granted membership, invite-code hijack, self-approval, second household, forged chore creator, non-assignee completion, assignee editing other fields, forged payer, non-member participants, fractional cents, duplicate participants, extra (image) fields, append-only ledger, forged sender IDs, message edits and deletes |

**Smoke test on the Android 17 emulator against the local Auth + Firestore emulators.** Account A was driven through the UI; Account B and outsider C used their own ID tokens over the emulator's REST API, so the rules applied to them too.
- ✅ A fresh launch shows Login; sign-up routes to Onboarding with the right name; A creates a household.
- ✅ B can't read the household before joining, and B's attempt to make itself a member is **denied**. B requests to join, **A approves in Settings**, and B completes the join.
- ✅ A assigns "Take out trash" to B in the UI. A can't complete it (disabled checkbox).
- ✅ B completes it, and **A's Chores screen updates live** to "Completed today".
- ✅ Outsider C: reading chores, completing the chore and posting a message are all **denied**. B forging a sender ID or renaming the chore is **denied**.
- ✅ Chat both ways (B → A shown with B's name; A → B readable by B). The chat draft survived rotating to landscape and back.
- ✅ Microphone permission denied → explanatory message, typing still works.
- ⚠️ The Android emulator process exited as soon as dictation began recording. That points to the emulator's host-microphone bridge, but it isn't confirmed. Restarting the emulator from the terminal then hung on boot, so the checks below were **not** run on a device.

## 8. Still pending for the demo
- **Firebase console (you):**
  - Enable the Google provider, add the SHA-1, re-download `google-services.json`.
  - Publish `firestore.rules`. The live project is still in default production mode, so nothing works on it until this is done.
- **Not yet verified on a device:**
  - Receipt scan → OCR → save expense, and the ledger display
  - Settings changes and their persistence across relaunch
  - Interruption recovery on the scan screen
  - Dictation with real speech
  - Google sign-in

  Run the demo checklist above. A **physical phone** is best for the camera and microphone. The emulator's virtual camera scene won't produce readable receipt text.
- **Instructor:** approval for Speech-to-Text as the microphone XP category.

---

*AI disclosure (L14): this app's code, rules and tests were written with the help of Claude (Anthropic's AI assistant), following the CS 4720 lecture slides and my Figma mockups. I reviewed and tested the result and can explain each screen and rule.*
