# ============================================================================
# AB Music - ProGuard / R8 rules  (minimal, safe, size-optimized)
# ============================================================================
# WHY THIS FILE IS SHORT:
# Modern AndroidX/Compose/Media3/Room/Hilt/Coil artifacts each ship their own
# "consumer-rules.pro" INSIDE the AAR. R8 reads and applies those rules
# automatically for every dependency on the classpath - you never have to
# hand-write "-keep class androidx.compose.** { *; }" yourself.
#
# The previous version of this file re-declared blanket keep rules
# (androidx.compose.**, androidx.media3.**, coil.** all with `{ *; }`) on
# top of those consumer rules. A `{ *; }` keep rule tells R8 "do not shrink,
# optimize, or obfuscate a single member of any class matching this
# pattern" - which is precisely why classes.dex stayed ~29MB despite
# minifyEnabled=true: R8 was told to keep basically everything.
#
# Removing the blanket rules below lets R8:
#   - strip every unused Material icon from material-icons-extended
#   - strip unused Compose Foundation/Animation/Material3 internals
#   - strip unused Media3 UI/Session code paths
#   - strip Coil's internal decoder/fetcher classes you don't use
#   - inline, merge and rename classes/methods across the whole app
# while the small set of rules below protects the handful of things R8
# cannot safely infer on its own (Room's runtime class-loading of
# "<Database>_Impl", and readable crash stack traces).
# ============================================================================

# --- Readable stack traces in crash reports (CrashReportScreen) ------------
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# --- Keep annotation attributes Compose's compiler plugin reads at runtime -
# (Compose's own consumer rules already keep @Composable metadata; this only
# preserves generic annotation visibility, a few hundred bytes at most)
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault

# --- Room -------------------------------------------------------------------
# Room instantiates "<YourDatabase>_Impl" by class name at runtime.
# Room's own consumer rules already keep @Entity/@Dao-annotated classes;
# this line is a belt-and-suspenders guard for the abstract database class
# itself, in case a future Room/AGP combo drops that consumer rule.
-keep class * extends androidx.room.RoomDatabase

# --- Domain models used as Room entities ------------------------------------
# Room's generated code accesses entity constructors/fields directly (no
# reflection), so no rule is required here. Field names are not otherwise
# read via reflection anywhere in this codebase (verified: no Gson, no
# kotlinx.serialization, no java.lang.reflect usage in domain/model/**).

# --- Kotlin coroutines: keep debug metadata off in release, but don't warn -
-dontwarn kotlinx.coroutines.debug.**

# --- Silence known-safe warnings from transitive libs -----------------------
-dontwarn org.checkerframework.**
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**

# ============================================================================
# Explicitly NOT kept (relying on library consumer-rules.pro + R8 shrinking):
#   androidx.compose.**        -> Compose Compiler + runtime ship their own
#   androidx.media3.**         -> Media3 ships its own (session/ui/exoplayer)
#   coil.**                    -> Coil ships its own
#   dagger.hilt.**             -> Hilt ships its own; generated components
#                                  reference each other directly, no runtime
#                                  reflection is used by Hilt at app runtime
#   kotlinx.parcelize.**       -> not used anywhere in this codebase (removed)
#   ViewModel <fields>/<methods> keep-all -> not needed; Hilt-generated
#                                  factories call ViewModel constructors
#                                  directly, and no code reflects over
#                                  ViewModel members anywhere in this app
# Activities/Services/Receivers declared in AndroidManifest.xml are kept
# automatically by AGP's built-in manifest-component keep rules - no manual
# -keep needed for MainActivity, MusicService, the widget receivers, etc.
# ============================================================================
