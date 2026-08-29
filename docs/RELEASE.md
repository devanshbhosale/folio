# Folio — Release Signing & Distribution

This project is set up to sign the release APK/AAB with your own upload key.

## 1) Generate an upload keystore (once)

```bash
keytool -genkey -v \
  -keystore folio-release.keystore \
  -alias folio \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

The tool asks for:
- store password (used again below)
- key password (usually same as store)
- your name / org / locality

Keep the resulting `folio-release.keystore` **outside the repo** and in a
secure backup — losing it means you cannot update the Play Store listing.

## 2) Wire it into Gradle

Create `keystore.properties` at the project ROOT (same folder as
`settings.gradle.kts`, NOT under `app/`):

```properties
storeFile=folio-release.keystore
storePassword=•••••••••
keyAlias=folio
keyPassword=•••••••••
```

`storeFile` is resolved relative to the project root, or as an absolute path.

`.gitignore` already ignores `keystore.properties` and `*.keystore` — never
commit either.

## 3) Build

```bash
./gradlew :app:assembleRelease
```

- **With** `keystore.properties` present → signed APK at
  `app/build/outputs/apk/release/app-release.apk`.
- **Without** it → the same build succeeds and emits
  `app-release-unsigned.apk`, which you can sign later:
  ```bash
  apksigner sign \
      --ks folio-release.keystore \
      --ks-key-alias folio \
      --out app-release-signed.apk \
      app-release-unsigned.apk
  ```

For an App Bundle (Play Store):
```bash
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

## 4) Verify

```bash
apksigner verify --print-certs app-release.apk
# should print the certificate fingerprint you created above
```

## 5) Play Console upload

- **Type:** App Bundle (`.aab`) — required for new apps since 2021.
- **Content rating:** everyone. No user data collected → declare "No data
  collected" in the Data safety form (offline app, no network permission).
- **Sensitive permissions:** none. Folio does not use `READ_MEDIA_*`,
  `MANAGE_EXTERNAL_STORAGE`, `POST_NOTIFICATIONS`, etc.
- **Target SDK:** 35 (Android 15). Meets 2026 Play policy.
