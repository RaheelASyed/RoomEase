# RoomEase: Manual Test Plan (Android Emulator)

Each test has steps, an **expected result** someone else could check (Lecture 14: "could two people disagree about whether it passed?"), and a box to tick.
- Record anything that fails in the **Bug log** at the bottom.
- Run the sections in order: later sections assume the household from section 2 exists.

---

## 0. Setup

### 0.1 Pick a backend (do one)

**Option A: local Firebase Emulator Suite (recommended while testing).** Nothing touches your real project, and you can inspect data at http://127.0.0.1:4000.

```bash
cd ~/AndroidStudioProjects/RoomEase/firestore-tests && npm install
```
```bash
cd ~/AndroidStudioProjects/RoomEase && ./firestore-tests/node_modules/.bin/firebase emulators:start --only auth,firestore --project <your-project-id>
```
In a second terminal, for **each** running emulator (use the serial shown by `adb devices`):
```bash
adb -s emulator-5554 reverse tcp:9099 tcp:9099 && adb -s emulator-5554 reverse tcp:8080 tcp:8080
```
```bash
./gradlew installDebug -Proomease.useEmulator=true
```
⚠️ Run `adb reverse` again every time an emulator restarts. Google sign-in (test A6) doesn't work against the Auth emulator in this setup; test it with Option B.

**Option B: your live Firebase project.** First publish `firestore.rules` in the Firebase console (Firestore → Rules → Publish). Until then every request is denied.
- Then run the app normally from Android Studio (▶ Run).

### 0.2 Devices
- **Best:** two emulators running side by side, so live updates are visible.
  - Device Manager → the ⋮ menu on *Medium_Phone* → **Duplicate**, name it *Phone B*, and start both.
  - Choose the target device from the device drop-down next to ▶ Run.
- **One emulator:** run the A/B steps one after another (sign out → sign in as the other account).
  - For "live update" tests, change the document from the Emulator UI (http://127.0.0.1:4000/firestore) while watching the app.
  - Edits made there skip the security rules, so this only checks the live UI, not permissions.

### 0.3 Test accounts
| Role | Name | Email | Password |
|---|---|---|---|
| A (owner) | Alice Test | alice@example.com | secret123 |
| B (member) | Bob Roommate | bob@example.com | secret123 |
| C (outsider) | Carol Outsider | carol@example.com | secret123 |

### 0.4 Emulator tools you'll use
| Need | How |
|---|---|
| Rotate | Rotate buttons on the emulator toolbar (or ⌘← / ⌘→) |
| Airplane mode | Swipe down for Quick Settings → Airplane mode |
| Simulated incoming call | Extended controls (⋯) → **Phone** → **Call device** |
| Dark mode | Quick Settings → Dark theme |
| Font size | Settings → Display → Font size → maximum |
| Kill app in background (process death) | Press Home, then run `adb shell am kill edu.virginia.cs4720.roomease`, then reopen from Recents |
| Fresh install state | `adb shell pm clear edu.virginia.cs4720.roomease` |
| Fake receipt for the camera | Extended controls → **Camera** → *Virtual scene images* → set the **Table** image to a receipt photo (PNG/JPG). In the camera view, hold ⌥ Option and use W A S D / mouse to look down at the table. Alternative: AVD → Edit → Advanced → Back camera = **Webcam0**, then hold a printed receipt up to your Mac's webcam. |
| Microphone | Extended controls → **Microphone** → turn on *Virtual microphone uses host audio input*. Also allow microphone access for Android Studio / qemu in macOS **System Settings → Privacy & Security → Microphone**. |

---

## 1. Sign-in and accounts (A)

- [ ] **A1 Fresh launch shows Login.** Run `pm clear`, then open the app.
  **Expected:** the RoomEase logo, "Welcome back", Email/Password fields, **Sign in**, **Continue with Google**, and "New here? Create account". No other screen flashes first.
- [ ] **A2 Validation before network.** On Login, type `alice@` and tap **Sign in**.
  **Expected:** "Enter a valid email address" under the email field; no spinner.
- [ ] **A3 Create account (A).** Tap **New here? Create account**. Enter Alice's details.
  - While the password is `abc`, the field shows "At least 6 characters" in red. If Confirm differs, it shows "Passwords don't match".
  - **Create account** stays disabled until every field is valid.
  - **Expected after submitting:** a spinner on the button, then *Set up your household* with "Hi Alice Test!".
- [ ] **A4 Wrong password.** Sign out (top-right **Sign out** on the onboarding screen), then sign in with Alice / `wrongpass`.
  **Expected:** "Email or password is incorrect." (readable text, not an exception), and the form stays filled in.
- [ ] **A5 Session restore.** Sign in correctly, swipe the app away from Recents, and reopen it.
  **Expected:** a short splash ("Getting your household ready"), then straight back to where Alice belongs. No Login screen.
- [ ] **A6 Google sign-in** *(Option B backend only; emulator must be a Google Play image signed into a Google account; SHA-1 and Google provider set up in the console)*. Sign out → **Continue with Google** → pick the account.
  **Expected:** a new Google user lands on onboarding; an existing member lands on Chores.
  - Cancelling the picker returns to Login with no error.
  - Before the console setup is done, a message says Google sign-in isn't set up yet, and the app doesn't crash.
- [ ] **A7 Password show/hide.** Tap the eye icon. **Expected:** the password text becomes visible, the icon changes, and TalkBack reads "Show password" / "Hide password".

## 2. Household onboarding (O)

- [ ] **O1 Create household (A).** Keep **Create household** selected, type `Maple House`, and tap **Create household**.
  **Expected:** you land on **Chores** with the bottom bar Chores / Expenses / Chat / Settings. **Settings** shows "Maple House", a 6-character invite code, and Alice as **Owner**.
- [ ] **O2 Invite code actions.** In Settings, tap the copy icon, then the share icon.
  **Expected:** the code is copied to the clipboard; the share sheet opens with "Join Maple House on RoomEase with invite code XXXXXX".
- [ ] **O3 Bad code (B).** Create Bob's account → **Join household**.
  - **Ask to join** stays disabled until 6 characters are entered.
  - Enter a well-formed wrong code like `ZZZZZZ`. **Expected:** "No household uses that code…".
- [ ] **O4 Request to join (B).** Enter Alice's real code → **Ask to join**.
  **Expected:** a "Waiting for approval" card for Maple House with **Cancel request**.
- [ ] **O5 Waiting survives restart (B).** Kill and reopen the app. **Expected:** it still shows "Waiting for approval".
- [ ] **O6 Owner approves (A).** Settings → **Join requests** shows Bob Roommate with **Approve** / **Decline**. Tap **Approve**.
  **Expected:** a confirmation message; the request disappears.
  - Bob's app shows "Approved! Joining…" and then opens Chores on its own (if Bob is signed out, this happens on his next sign-in).
  - Alice's member list shows 2 members.
- [ ] **O7 Decline path (C).** Carol creates an account and asks to join with the same code. Alice taps **Decline**.
  **Expected:** Carol sees "Request declined" with **Use a different code**; Carol never sees any household screens.
- [ ] **O8 Cancel request.** Carol asks again, then taps **Cancel request**. **Expected:** she's back on the Create/Join form, and Alice's Join requests list empties.

## 3. Privacy and permissions (S)

These check the server rules through the real UI. The full rule suite runs with `cd firestore-tests && npm test`.

- [ ] **S1 Outsider sees nothing.** As Carol (not a member), the app stays on onboarding.
  - In the Emulator UI → Firestore, Maple House's chores and messages exist, yet nothing from them appears anywhere in Carol's app.
- [ ] **S2 Non-assignee can't complete.** On a chore assigned to Bob, Alice's checkbox is greyed out. TalkBack reads "Only Bob Roommate can complete this".
- [ ] **S3 Rule tests pass.** Run `cd firestore-tests && npm test`. **Expected:** `pass 28`, `fail 0`.

## 4. Chores (C)

- [ ] **C1 Empty state.** New household, Chores tab with **My chores** selected. **Expected:** "Nothing assigned to you" with **Add a chore**.
- [ ] **C2 Save disabled until valid.** **+ New chore** → **Save chore** is disabled while the title is empty.
  - The title stops at 80 characters, with a counter.
- [ ] **C3 Date picker.** Tap the due-date button. **Expected:** a calendar where past dates can't be picked. After choosing, the button shows e.g. "Friday, Nov 20".
- [ ] **C4 A assigns to B (core demo).** As Alice: title `Take out trash`, select **Bob Roommate**, pick a date → **Save chore**.
  **Expected:** you return to Chores. Under **All chores** the row reads "Bob Roommate · Due tomorrow" with a BR avatar.
- [ ] **C5 B completes; A sees it live (core demo).** On Bob's device, the chore is under **My chores**. Tap its checkbox.
  **Expected:** on Bob's device, the title is struck through and reads "Completed today".
  - On **Alice's device, without touching anything, within a couple of seconds** the row changes to struck through, "Completed today", and the progress card shows "1 of 1 chores done".
- [ ] **C6 Undo.** Bob unticks the box. **Expected:** both devices show it as open again.
- [ ] **C7 Sorting.** Create three chores with different due dates and complete one.
  **Expected:** open chores come first (soonest due at the top); completed ones are at the bottom.
- [ ] **C8 Overdue.** In the Emulator UI, edit a chore's `dueDate` to yesterday. **Expected:** "Overdue · yesterday" in red.
- [ ] **C9 Double-tap save.** Fill the form and tap **Save chore** twice very quickly. **Expected:** exactly one new chore.

## 5. Expenses and ledger (E)

- [ ] **E1 Empty state.** **Expected:** "You owe $0.00 | You're owed $0.00", Bob listed as "Settled up", and the ledger shows "No shared expenses yet".
- [ ] **E2 Manual expense, even split.** As Alice: **Add expense** → description `Groceries`, total `10` → leave both roommates selected → **Save expense**.
  **Expected:** the split preview shows $5.00 each before saving. After saving:
  - Alice: "You're owed $5.00"; Bob's row reads "Owes you $5.00".
  - Bob's device: "You owe $5.00".
- [ ] **E3 Odd cents.** Add `$10.01` split between A and B. **Expected:** one person shows $5.01 and the other $5.00, with the note "Leftover cents go one each…". Both devices agree on who has $5.01.
- [ ] **E4 Amount validation.** Type `12.345`, then `abc`. **Expected:** "Enter an amount like 12.50", and **Save expense** is disabled.
- [ ] **E5 Deselect everyone.** Untick all people. **Expected:** "Pick at least one person." and Save is disabled.
- [ ] **E6 Ledger row details.** Tap an expense row. **Expected:** it expands to show each person's share (and the merchant, if any). Tap again to collapse.
- [ ] **E7 Totals card.** **Expected:** "Totals (paid − share)" lists each member; the values add up to $0.00 overall.

## 6. Receipt scanning (R)

Set up the fake receipt first (section 0.4). A clear, flat, well-lit receipt image works best.

- [ ] **R1 Camera permission asked in context.** **Add expense** → **Scan receipt**.
  **Expected:** a "Camera access" card explains why, with an **Allow** button. Tapping it shows the system dialog. Nothing is asked at app launch.
- [ ] **R2 Deny → manual entry.** Choose **Don't allow**.
  **Expected:** "Permission is currently off…" with **Try again**, **Open app settings**, and **Enter the expense by hand**. The last one returns to the form, which still works.
- [ ] **R3 Allow → in-app preview.** Allow the camera. **Expected:** a live camera preview inside RoomEase (no other camera app opens), with a round shutter button.
- [ ] **R4 Capture → review → retake.** Tap the shutter. **Expected:** "Review receipt" with the photo, the note "Photo stays on this device…", and **Retake** / **Use photo**. **Retake** returns to the live preview.
- [ ] **R5 Use photo → suggestion.** Capture again → **Use photo**.
  **Expected:** a spinner ("Reading receipt…"), then back to the form with the merchant and total filled in and the green message "Filled in from your receipt…". Every field stays editable.
- [ ] **R6 Unreadable receipt.** Point the camera at a blank wall → **Use photo**.
  **Expected:** a red message "We couldn't read this receipt. Enter the details below." The form stays usable.
- [ ] **R7 Save with receipt.** Correct the fields if needed → **Save expense**.
  **Expected:** the ledger row has a phone icon ("Receipt photo saved on this device"). Expanding it shows the photo and the "stays on this device" note.
  - On **Bob's** device, the same expense shows "Receipt photo is on the phone that scanned it."
  - In the Emulator UI, the expense document has **no image field**, only `merchant`, `totalCents`, `hasReceipt`, etc.
- [ ] **R8 Remove photo.** Before saving, tap **Remove photo**. **Expected:** back to the **Scan receipt** button; the expense saves without a receipt.

## 7. Chat (M)

- [ ] **M1 Empty state.** **Expected:** "No messages yet".
- [ ] **M2 Two-way live chat.** Alice sends `Hi Bob`. **Expected:** on Bob's device it appears within seconds, on the left with "Alice Test" and an AT avatar. Bob replies, and it appears on Alice's device.
- [ ] **M3 Send disabled when empty.** **Expected:** the send button is greyed out when the box is empty or only spaces.
- [ ] **M4 Pending vs synced.** Turn on **airplane mode** on Alice's device and send `Offline test`.
  **Expected:** the bubble appears right away with a clock icon and "Sending…". Turn airplane mode off → it changes to a time like "3:42 PM", and Bob receives it.
- [ ] **M5 No double send.** Type a message and tap send several times quickly. **Expected:** it's sent once; the box clears after the first tap.
- [ ] **M6 Draft survives rotation.** Type `draft test` (don't send) → rotate to landscape and back. **Expected:** the text is still there.
- [ ] **M7 Draft survives app restart.** Type `restart test` → wait 1 second → swipe the app away → reopen → Chat. **Expected:** the text is still there.

## 8. Dictation (D)

⚠️ In earlier testing the emulator closed when recording started, probably because the emulator's host-microphone connection wasn't set up. Set up the microphone first (section 0.4), or run D3–D5 on a physical phone.

- [ ] **D1 Asked only on tap.** Tap the mic. **Expected:** the system dialog "Allow RoomEase to record audio?" appears now, not at launch.
- [ ] **D2 Deny → typing still works.** **Don't allow**. **Expected:** the red note "Microphone permission denied. You can keep typing…" with **OK**; the text box still accepts typing.
- [ ] **D3 Dictate → review → send.** Allow, tap the mic, and say "pizza at seven".
  **Expected:** a red dot and "Listening…" with live partial words; the icon turns into a stop button. When you finish, the text appears **in the composer** (not sent). Edit it, then send manually.
- [ ] **D4 Recognition error.** Tap the mic and stay silent. **Expected:** "Didn't catch that. Try again, or type your message." No crash.
- [ ] **D5 Interruption releases the mic.** Start dictating, then press Home. **Expected:** listening stops; the emulator's mic indicator turns off. Return to the app: the composer still works.

## 9. Settings in DataStore (P)

- [ ] **P1 Theme.** Settings → Theme **Dark** → the whole app turns dark right away. **Light** → light even when the emulator is in dark mode. **System** → follows the emulator's Quick Settings dark theme.
- [ ] **P2 Default chore view.** Set **All chores** → open the Chores tab. **Expected:** **All chores** is selected when it opens.
- [ ] **P3 Default split.** Set **Choose manually** → **Add expense**. **Expected:** only "(you)" is ticked. Switch back to **All roommates** → a new expense starts with everyone ticked.
- [ ] **P4 Survives relaunch.** Set Dark / All chores / Choose manually → swipe the app away → reopen.
  **Expected:** the app opens dark, Chores opens on All, and a new expense starts with only you selected.
- [ ] **P5 Sign out.** Tap **Sign out** (red, at the bottom). **Expected:** the Login screen; Back doesn't return into the app.

## 10. Lifecycle and interruptions (L)

- [ ] **L1 Rotation keeps the screen and form.** On **New expense**, fill in a description, an amount, and the people. Rotate twice. **Expected:** same screen, all values kept.
- [ ] **L2 Process death keeps the form.** Same form, press Home → `adb shell am kill edu.virginia.cs4720.roomease` → reopen from Recents.
  **Expected:** back on **New expense** with the description, amount and people restored. The password is never restored on the auth screens; that's intentional.
- [ ] **L3 Receipt preview survives recreation.** Capture a receipt and stay on **Review receipt** → rotate → kill as in L2 → reopen. **Expected:** the same photo is still shown.
- [ ] **L4 Incoming call during camera.** On the live camera preview, Extended controls → Phone → **Call device**, then end the call.
  **Expected:** the preview stops during the call and resumes afterwards; no crash, no frozen preview.
- [ ] **L5 Selected record kept.** Expand an expense row → rotate. **Expected:** it stays expanded.
- [ ] **L6 Tab state.** Switch to Chat → rotate → **Expected:** still on Chat. Press Back → goes to Chores → Back again leaves the app.
- [ ] **L7 Offline chore.** Airplane mode on → create a chore. **Expected:** it appears at once with a **Pending sync** chip; the chip disappears after reconnecting.

## 11. Accessibility and UI quality (U)

- [ ] **U1 Large font.** Set the system font size to maximum. **Expected:** text grows on every screen and wraps; nothing is cut off or overlapping (compare with the `fontScale = 1.5` previews).
- [ ] **U2 TalkBack spot-check.** Turn on TalkBack (Settings → Accessibility). **Expected:** icon buttons are announced ("Send message", "Dictate a message", "Copy invite code", "Take photo of receipt", "Back"); chat bubbles read "Bob Roommate: …".
- [ ] **U3 Touch targets.** Every tappable control (checkboxes, icon buttons, segmented buttons) is easy to hit. Layout Inspector shows at least 48dp.
- [ ] **U4 Dark-mode contrast.** In Dark theme every screen's text is readable, including the error red, the green "You're owed", and chat bubbles.
- [ ] **U5 Loading and error states.** Kill the Firebase emulator (Ctrl-C) while the app is open → open each tab.
  **Expected:** a spinner and/or "Couldn't load this" with **Try again**. Restart the emulator and run `adb reverse` again → **Try again** recovers.

---

## Results summary
| Section | Pass | Fail | Notes |
|---|---|---|---|
| 1 Sign-in | | | |
| 2 Onboarding | | | |
| 3 Privacy | | | |
| 4 Chores | | | |
| 5 Expenses | | | |
| 6 Receipt scanning | | | |
| 7 Chat | | | |
| 8 Dictation | | | |
| 9 Settings | | | |
| 10 Lifecycle | | | |
| 11 Accessibility | | | |

## Bug log
| ID | Test | Steps to reproduce | Expected | Actual | Device/API | Severity |
|---|---|---|---|---|---|---|
| | | | | | | |
