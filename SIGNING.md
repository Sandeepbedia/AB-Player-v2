# Release Signing Setup (JKS Keystore)

Debug builds (`./gradlew installDebug`) do **not** need any of this —
Android auto-generates a debug keystore. This guide is only for
building a signed **release** APK/AAB.

> ⚠️ Never commit your `.jks` file or real passwords to the repo.
> `keystore.properties` and `*.jks` are already git-ignored (see below).

## 1. Generate a keystore (one-time, per developer/maintainer)

```bash
keytool -genkeypair -v \
  -keystore ab-player-release.jks \
  -alias ab-player-key \
  -keyalg RSA -keysize 2048 -validity 10000
```

You'll be prompted for:
- Keystore password
- Key password (can be same as keystore password)
- Your name / org details (used only in the cert, not shown to users)

Keep `ab-player-release.jks` **outside the repo** (e.g. in a secure
folder, or a password manager / secrets vault) — do not place it in
`app/`.

## 2. Create `keystore.properties`

In the project root, create a file named `keystore.properties`
(this file is git-ignored, never committed):

```properties
storeFile=/absolute/path/to/ab-player-release.jks
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=ab-player-key
keyPassword=YOUR_KEY_PASSWORD
```

## 3. Wire it into `app/build.gradle`

```kotlin
import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

If `keystore.properties` is missing (e.g. on a contributor's machine
or a CI job without secrets), the release signing config simply stays
empty and the build falls back to unsigned — it won't break the
build for contributors who only need debug builds.

## 4. `.gitignore` entries (already required)

Make sure these lines exist in `.gitignore`:

```
keystore.properties
*.jks
*.keystore
```

## 5. Building a signed release

```bash
./gradlew assembleRelease
# or for Play Store / App Bundle:
./gradlew bundleRelease
```

Output: `app/build/outputs/apk/release/` or
`app/build/outputs/bundle/release/`.

## 6. CI/CD (GitHub Actions) — optional

If you sign releases via GitHub Actions instead of locally:

1. Base64-encode the `.jks` and store it as a repo secret
   (`RELEASE_KEYSTORE_BASE64`), plus `KEYSTORE_PASSWORD`,
   `KEY_ALIAS`, `KEY_PASSWORD` secrets.
2. In the workflow, decode the secret to a file before the build step:
   ```yaml
   - name: Decode keystore
     run: echo "${{ secrets.RELEASE_KEYSTORE_BASE64 }}" | base64 -d > ab-player-release.jks
   - name: Write keystore.properties
     run: |
       echo "storeFile=${{ github.workspace }}/ab-player-release.jks" >> keystore.properties
       echo "storePassword=${{ secrets.KEYSTORE_PASSWORD }}" >> keystore.properties
       echo "keyAlias=${{ secrets.KEY_ALIAS }}" >> keystore.properties
       echo "keyPassword=${{ secrets.KEY_PASSWORD }}" >> keystore.properties
   ```
3. Never echo secrets into logs; GitHub Actions masks them automatically
   as long as they're referenced via `secrets.*`.

## Only maintainers need real signing keys

Regular contributors submitting PRs **never** need the release
keystore — they only build/test with the debug config. Keep the
release `.jks` and passwords restricted to the project owner(s).
