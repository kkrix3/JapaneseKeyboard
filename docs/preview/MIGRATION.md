# Official Full -> Full Preview migration

## Findings from the reviewed source

General settings and custom keyboard backups are different formats. Latest AppPreference
still exports default SharedPreferences as version=1, entries=[key,type,value]. It does not
check the exporting applicationId or edition. Custom haptics uses these same preferences,
so its raw waveform strings and media boolean are included without a new backup format.

Import accepts unknown **keys** of recognized types. There is no Full-only key whitelist;
Full settings imported into Lite can remain stored even where the feature is unavailable.
Unknown **types** are skipped. Recognized types are cast directly; a bad type/value pair
can throw. A value stored under the wrong type can also fail when a typed getter reads it.
The import does not validate all key-specific schemas. These are existing limitations,
not a reason to rewrite migration speculatively. No data-migration implementation changed.

Default import uses replaceAll=true. It replaces the destination preference set, so a later
official backup can remove Preview-specific waveform values absent from that backup. Export
Preview settings first before repeating an official-settings import. Waveform defaults then
apply to missing values. A package change separates app storage but does not make this JSON
intrinsically incompatible. Room data/files/URI grants are not transported by this JSON.

For custom keyboards specifically, the PoC v1.7.111 parser accepts schemaVersion <=3,
whereas latest dev exports and accepts schemaVersion 4. The new textInputBehavior field
supports upstream toggle behavior. A v4 keyboard backup is rejected by the old parser;
this is a concrete version incompatibility independent of Full/Lite. Full Preview retains
the latest v4 parser, the legacy formats, validation and stable-ID import behavior. Do not
renumber a v4 file to 3 for migration into this new Preview: retain its actual semantics.

No real official-installed APK/settings backup has been imported into a Preview APK yet.
Robolectric tests passed on both feature and Preview in CI run 35032069860 for separate
preference namespaces, supported types, unknown keys/types and waveform round-trip.
This automated compatibility check is not an end-to-end device migration success claim.

## What needs a separate export

| Data | Transfer path found in current source |
| --- | --- |
| General settings, waveform patterns, TOUCH/MEDIA choice | General settings JSON |
| Custom keyboards and their mappings | Custom keyboard list -> export/import, keyboard_layouts_backup.json (v4) |
| User dictionary | User dictionary -> export/import, user_dictionary_backup.txt |
| Learned dictionary | Learning dictionary -> export/import, learned_dictionary_backup.json |
| Templates | Templates -> export/import, user_template_backup.txt |
| NG words | NG words -> export/import, ng_words.json |
| Custom romaji maps | Romaji maps -> export/import, romaji_maps_backup.json |
| Text macros | Text macros -> export/import, text_macro_backup.json |
| N-gram rules | N-gram rules -> export/import, ngram_rules.json |
| Custom zero-query dictionary | Its editor -> export/import, custom_zero_query_dictionary.json |
| Clipboard history | Clipboard history -> export/import, clipboard_history_backup.json (optional sensitive data) |
| Built system user dictionary | Dedicated builder exports its dictionary file; reselect/import or rebuild as offered by the UI |
| Selected models, images, wallpapers, external files and URI permissions | File contents/grants are not copied by settings JSON; reselect/regrant or redownload as needed |
| Other Room tables, derived caches and internal files | No universal DB migration is added; do not assume the above exports preserve every table/cache |

AppModule retains the upstream Room database name `learn_database` and explicit migration
chain. ApplicationId scopes the database to Preview. Future updates must retain both this
identity and upstream database migration paths; stable signing alone cannot prove data safety.

## First installation

1. Keep official Sumire installed. Export its general settings and the individual data
   categories you use. Keep those backups outside either app's internal storage.
2. Install the first **signed Full Preview** APK; enable Sumire Preview in Android's IME
   settings. The package is com.kazumaproject.markdownhelperkeyboard.preview.
3. Import general settings through general settings import. Import each separate backup
   through its matching editor. Do not feed a keyboard backup to general-settings import.
4. Regrant external file access and check selected keyboard layouts/links/order. Where a
   setting refers to a former numeric DB ID, reselect the imported target after import.
5. Check full features (Zenz/Gemma), dictionary entries and actual typing. Then configure
   custom waveforms if the official backup did not contain them.
6. Export a fresh Preview backup once migration has been verified. Keep official data
   until satisfied; the apps' data stores remain separate.

## A -> B update verification

Generate A and B from two new signed workflow runs after user-managed signing is configured.
Use the same Environment secrets for both. On a computer with Android SDK tools:

```bash
python3 scripts/preview/verify_update_pair.py Preview-A.apk Preview-B.apk
adb install Preview-A.apk
# On the device, enable Preview and set a distinctive preference, custom waveform,
# keyboard, dictionary entry and template. Record their expected values.
adb install -r Preview-B.apk
adb shell ime list -s
adb shell dumpsys package com.kazumaproject.markdownhelperkeyboard.preview
```

The helper verifies package, valid identical signing certificates and strictly greater
versionCode. It does not inspect secrets or install anything itself. The device commands
must target your intended device; if multiple are connected, use adb -s SERIAL.
Do not uninstall A or use downgrade flags. Installation must report Success. Confirm IME
selection/enabled state, actual typing, every recorded preference and DB item, then restart
the phone and recheck. Android may stop an IME during app update; reselect it if needed,
and record whether that was necessary. Keep that observation separate from data retention.

## Device checklist (all pending for this port)

1. Normal flick: weak single pulse only when released/accepted.
2. Two-step flick: one waveform containing two distinct pulses; first-stage release stays normal.
3. Special key: stronger single pulse; repeat loops do not add an extra release pulse.
4. TOUCH mode: respects system touch haptics, including OFF.
5. MEDIA mode: uses media routing; device policy may affect output.
6. Rapid repeated input and split panes: no stale gesture classification or duplicate feedback.
7. Weak pulse after strong pulse: test raw (1ms,32) tail manually; no automatic tail insertion.
8. Official Sumire and Preview coexist with distinct launcher/IME names.
9. A -> B installation succeeds without uninstalling and has the same certificate.
10. Preferences, waveforms, custom keyboards and dictionaries survive updating/restarting.
11. Actual official Full backups restore through the correct screens.
12. Zenz/Gemma and other Full-only functionality work; models/permissions are available.

Also verify old press/release/both, long-press Delete/cursor, empty/silent patterns, editor
save/cancel/recreation, upstream toggle input and hierarchical/sticky two-step controllers.
Physical timing/intensity, OS policy and actual editor behavior cannot be proved by JVM tests.
