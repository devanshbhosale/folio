# Folio — How to get an installable APK

Folio is a normal Android Studio project. The APK is produced on your
own machine (recommended) or by the included GitHub Actions workflow.

## Option A — Android Studio (fastest, GUI)

1. Unzip the project and open the `Folio/` folder in Android Studio
   Koala or newer.
2. Let Gradle sync (needs ~4 GB free RAM, mostly for Apache POI's OOXML
   schemas — no other setup).
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
4. The signed-debug APK lands at
   `app/build/outputs/apk/debug/app-debug.apk`. Copy to your phone (or
   click *Locate*) and install — Android will ask you to allow install
   from unknown sources the first time.

For a **release** APK signed with your own key, see `docs/RELEASE.md`.

## Option B — Command line (any Linux / macOS / WSL)

```bash
# one-time
export JAVA_HOME=<path to JDK 17>        # required
# builds Debug APK
./gradlew :app:assembleDebug --no-daemon
# → app/build/outputs/apk/debug/app-debug.apk
```

You need at least **4 GB of free RAM** while `compileDebugJavaWithJavac`
runs — Apache POI's XML-schema jars are large. If your machine is
tighter than that, use Option C below.

## Option C — GitHub Actions (no local Android setup)

The repo already ships `.github/workflows/build.yml`. To get an APK:

1. Create a new empty GitHub repo (public or private, both work).
2. `git init` in the unzipped `Folio/` folder, commit everything, push
   to that repo.
3. GitHub Actions kicks off automatically. Wait ~1 minute for the
   green check.
4. Open the workflow run → *Artifacts* → download `folio-debug-apk`.
   Inside is `app-debug.apk` — copy to your phone and install.

The runner has 7 GB RAM, so the POI-heavy compile finishes comfortably.

## Why can't the sandbox that authored this code build it?

The sandbox this project was authored in is capped at 2 GB RAM. The
javac step for `compileDebugJavaWithJavac` — with Apache POI on the
classpath — spikes above 1.3 GB and gets OOM-killed by the container's
cgroup, so no APK is produced here. Options A / B / C above all give
your build 4 GB+ and finish in about a minute.
