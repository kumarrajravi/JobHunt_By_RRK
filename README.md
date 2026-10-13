# JobHunt_byRRK (Android)

Screens live in app/src/main/assets/index.html. Native features (Gmail app, updater, back button, Apify/AI calls) are in app/src/main/java/com/rrk/jobhunt/MainActivity.java.

## One-time setup on GitHub (repo kumarrajravi/JobHunt_By_RRK)
1. Delete the old files in the repo, then upload everything in this folder EXCEPT the hidden .github folder.
2. Add file > Create new file. Name: .github/workflows/build.yml  (typing / makes folders). Paste BUILD_WORKFLOW_COPY_ME.yml. Commit.
3. Settings > Secrets and variables > Actions > New repository secret. Add KEYSTORE_BASE64, KS_PASS, KEY_PASS, KEY_ALIAS using SIGNING_SECRETS_PRIVATE.txt (do NOT upload that file or the keystore to the repo). The same key must be used for every release, otherwise updates will not install.
4. Make the repo Public (the app checks releases without a login). The APK contains no secrets, keys are typed on the phone.

## Publish a version (v1.0, then v1.1, ...)
Releases > Draft a new release > Choose a tag: type v1.0 > Create new tag > Publish release.
The workflow builds the signed APK and AAB and attaches them to that release in about 5 minutes.
For an update later, publish a new release with a higher tag (v1.1). Installed apps show an Update banner and install it.

## Install
Download JobHunt_byRRK-v1.0.apk from the release page on your phone, install it. Open Settings, paste the Apify token (and optionally an OpenAI or Anthropic key).
Uninstall the earlier Jobflow app first.

## Play Store
Upload the .aab from the release. You need a Play developer account, a privacy policy, store listing and screenshots.
