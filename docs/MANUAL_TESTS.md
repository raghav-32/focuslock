# Manual test checklist (physical Android phone)

**None of these have been run.** They need a real device with the app installed. Mark each as you go.
Test on at least one stock-like phone (Pixel) and one with aggressive battery management (Xiaomi / Samsung / Oppo) if possible.
Use throwaway test apps (e.g. a game or Calculator) before blocking anything you rely on.

Legend: **A** = also covered by an automated test (JVM unit test in `app/src/test` or `androidTest`), but the on-device behaviour still needs a manual check.

## Setup and permissions
- [ ] 1. First launch with no permissions: onboarding appears; each step explains its permission; "Skip for now" works for every step. (UI, manual)
- [ ] 2. Accessibility step opens Android's Accessibility settings; after enabling FocusLock the step shows "On and running" without restarting the app.
- [ ] 3. On Android 13+ with a sideloaded APK: switch is greyed out → App info → "Allow restricted settings" → works.
- [ ] 4. Finish onboarding with Accessibility OFF: Home shows the red "Blocking is OFF" banner and its button opens Permissions.
- [ ] 5. Revoke while running: with the app open, turn Accessibility off in system settings and come back. Banner appears within a few seconds. Notification "FocusLock is not blocking" appears (once per 12 h max).
- [ ] 6. Usage access off: Apps list hides usage, Stats says usage is off and offers a button; granting it makes numbers appear.
- [ ] 7. Notifications denied: Notification settings screen shows the warning and a button to open system settings.

## Whole-app blocking
- [ ] 8. App list shows icons, names, search, All / Blocked / Allowed chips, system-app toggle; essential apps (launcher, dialer, Settings, keyboard, FocusLock) never appear. (A: discovery exclusions are device-specific)
- [ ] 9. Block one app → open it → "This App Is Blocked" appears with the app name and reason "It is on your always-blocked list."
- [ ] 10. Block three apps; open each in turn; all are blocked. Unblock one → it opens normally.
- [ ] 11. "Return to Home" goes to the launcher. System Back on the block screen also goes home (no loop). "View My Progress" opens Stats.
- [ ] 12. Rapid switching: open blocked app, press Home, reopen within a second, repeat quickly. Every open is blocked, no stacked block screens, no flicker loop. (A: BlockGateTest)
- [ ] 13. Long-press selects multiple; "Block" with more than 10 selected shows a confirmation; selection and toggles survive killing the app and rebooting the phone.
- [ ] 14. Per-app "Only during focus sessions and schedules": app opens normally outside a session, is blocked during one.

## Focus sessions
- [ ] 15. Start 5-minute session: block works, remaining time ticks on Focus tab and Home, "session started" notification appears. (A: rule logic)
- [ ] 16. Let it finish: notification "Focus session complete" appears once; apps open again. Check the screen both open and closed. (A: reconcile test)
- [ ] 17. Cancel a normal session early: confirmation, blocks stop immediately.
- [ ] 18. Strict session: no cancel button, Home master switch locked, Emergency access hidden, rules can't be loosened (unblock shows the strict message). Session still ends on time.
- [ ] 19. Kill FocusLock from recents mid-session; reopen: session still counted; blocking still works if the service is running.
- [ ] 20. Reboot mid-session: after boot the remaining time is correct (wall-clock based) and blocking resumes once the accessibility service starts. (A: SessionClockTest)
- [ ] 21. Change the phone's clock +/- hours mid-session (disable automatic time): remaining time does not jump. (A: SessionClockTest)

## Schedules
- [ ] 22. Create "Study 7:00 AM–12:00 PM" weekdays for one app; set it to start 2 minutes from now: block begins on time (alarm may be a little late in Doze, blocking itself starts on the next app event) and "Scheduled blocking activated" appears. (A: RuleEngineTest, ScheduleEvaluatorTest)
- [ ] 23. Schedule ends: notification "Schedule ended", app opens again.
- [ ] 24. Overnight schedule (10 PM–6 AM): editor says "runs overnight"; blocks at 11 PM and 3 AM, not at 7 AM. (A)
- [ ] 25. Change time zone in system settings: schedule stays on the same clock times. (A)
- [ ] 26. Start a schedule while already inside the blocked app: block appears within about a second. (service re-evaluates on content changes)
- [ ] 27. Disable a schedule via its switch: no blocking.

## Feed rules (Shorts / Reels)
These depend on installed app versions; record the version you tested.
- [ ] 28. Turn on "YouTube Shorts" rule. Open YouTube home: NOT blocked. Open a Short: block screen "YouTube Shorts is blocked." and you are taken back one screen. (If it doesn't trigger, the shipped view-id names don't match your version: see step 31.)
- [ ] 29. Stay on the same Short screen: only one block screen is shown (no repeated launches). (A: BlockGateTest)
- [ ] 30. Repeat for Instagram Reels and Facebook Reels. Facebook is expected to be unreliable.
- [ ] 31. Unsupported feed: if detection fails, tick "Block the whole app instead": the whole app is blocked as a fallback.
- [ ] 32. Edit detection names; Restore default rules; confirm dialogs appear.

## Bypass, groups, presets
- [ ] 33. Bypass = Confirm: "Emergency access" → Yes → app opens for the configured minutes, then is blocked again. A second request within the cooldown is refused with a message. (A: PolicyAndStatsTest)
- [ ] 34. Bypass = Wait then open: countdown shows and can be cancelled; app opens when it reaches zero.
- [ ] 35. Bypass = None: no button. Strict session: no button regardless of setting.
- [ ] 36. Always-allowed app (Settings > Emergency access) is never blocked.
- [ ] 37. Preset "Games" shows the exact apps it would affect and does nothing until you tap a button; "Create group only" doesn't block anything.
- [ ] 38. An app in two groups works; deleting a group doesn't unblock apps.

## Statistics, backup, privacy
- [ ] 39. After several blocks: Home "Blocked today", Stats day/week/month, weekly progress chart all show real numbers and the time-saved card is labelled an estimate.
- [ ] 40. Export backup → delete a few rules → import backup → rules return. Import a random text file → "Not restored", nothing changes. (A: BackupCodecTest, with a stand-in JSON parser)
- [ ] 41. Export statistics CSV opens in a spreadsheet.
- [ ] 42. Reset statistics and Reset settings show confirmations and do what they say; rules survive.
- [ ] 43. Privacy screen text matches reality: verify in Settings > Apps > FocusLock > Permissions that there is no network/location/contacts/etc.

## Environment
- [ ] 44. Dark and light theme (Settings) both render the block screen and main screens legibly; system theme follows the phone.
- [ ] 45. Airplane mode: everything still works (no network needed).
- [ ] 46. Force-stop FocusLock (Settings > Apps > Force stop): blocking stops (expected, can't be prevented). Open FocusLock: the service is re-bound only if it is still enabled in Accessibility; otherwise the banner explains it.
- [ ] 47. Service failure/recovery: toggle Accessibility off/on; banner disappears and blocking resumes without reopening FocusLock.
- [ ] 48. Large app list (150+ apps): scrolling and search stay smooth, no ANR.
- [ ] 49. Low-RAM device or "Don't keep activities": app doesn't crash on rotation or process death.
- [ ] 50. Recovery guide (Settings): all steps make sense; "Open Accessibility settings" works.
