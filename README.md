# Jobflow (Android)
A small WebView app. The screens live in app/src/main/assets/index.html. Apify calls go through a native bridge in MainActivity.java.

## Get the APK without installing anything
1. Create a free GitHub account and a new repository (private is fine).
2. Upload everything in this folder EXCEPT the hidden .github folder (drag and drop). Then on GitHub click Add file > Create new file, type  .github/workflows/build.yml  as the name (typing / makes folders), paste the contents of BUILD_WORKFLOW_COPY_ME.yml, and click Commit changes.
3. Open the Actions tab, pick "Build APK", click Run workflow. After about 5 minutes open the run and download the "jobflow-apk" artifact (a zip containing app-debug.apk).
4. Copy app-debug.apk to your phone and install it (allow "install unknown apps" once).
5. Open Jobflow, tap Settings, paste your Apify API token (Apify Console, Settings, API and integrations).

## Or build locally
Open this folder in Android Studio and press Run, or run `gradle assembleDebug`.

## Notes
- The token is stored only in the app's local storage on your phone. Do not share the APK after you have pasted a token.
- Each search runs the Apify actor curious_coder/linkedin-jobs-scraper once per role (up to 3) and uses your Apify credits.
- Without a token the app shows sample jobs.
