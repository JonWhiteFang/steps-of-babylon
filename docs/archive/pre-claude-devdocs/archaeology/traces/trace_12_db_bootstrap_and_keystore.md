# Trace 12 — DB bootstrap: SQLCipher + Keystore passphrase

*Phase 3 Deep Trace. Ground truth:
`StepsOfBabylonApp.kt`,
`di/DatabaseModule.kt`,
`data/local/DatabaseKeyManager.kt`,
`data/local/AppDatabase.kt`,
`data/local/Migrations.kt`,
`data/local/Converters.kt`. This is the very first thing that happens
in any app process.*

## 1. Entry Point

Three ways to kick off the DB bootstrap:

1. **App cold start (any)** — `StepsOfBabylonApp.onCreate` runs first.
2. **WorkManager wakes process** — same `onCreate` runs because Hilt
   initialisation order runs app-first.
3. **BootReceiver restart** — same; the service is not started until
   `MainActivity` or `BootReceiver` calls `startForegroundService`, but
   `StepsOfBabylonApp.onCreate` fires earlier on process creation.

The actual *first DB access* — i.e. the first DAO call — happens later
when the first Hilt consumer needs it (typically in a VM's `init` block
or `viewModelScope.launch`).

## 2. Execution Path

### 2.1 `StepsOfBabylonApp.onCreate`

```kotlin
@HiltAndroidApp
class StepsOfBabylonApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")                [mandatory BEFORE any Room access]
        StepSyncScheduler.schedule(this)               [WorkManager KEEP policy]
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
```

- `System.loadLibrary("sqlcipher")` loads `libsqlcipher.so` from the
  APK. Room won't use the custom open-helper factory without this;
  `SupportOpenHelperFactory(passphrase)` internally does JNI calls
  into the native lib.
- `StepSyncScheduler.schedule` is covered in trace 02.
- `workManagerConfiguration` is read by WorkManager during its
  initialisation (via `Configuration.Provider`). Because the manifest
  disables `androidx.work.WorkManagerInitializer` startup provider
  (tools:node="remove"), WorkManager defers to this getter the first
  time `WorkManager.getInstance(context)` is called — which is inside
  `StepSyncScheduler.schedule`.

### 2.2 Hilt-driven DB construction (lazy)

When the first injection point that needs `AppDatabase` is invoked
(e.g. a DAO in a repository in a VM), Hilt traverses the module graph:

```
Consumer (e.g. PlayerRepositoryImpl)
  ← @Inject constructor(PlayerProfileDao)
    ← @Provides PlayerProfileDao(db: AppDatabase) = db.playerProfileDao()
      ← @Provides @Singleton provideDatabase(@ApplicationContext context) = ...
```

`DatabaseModule.provideDatabase`:

```kotlin
val passphrase = DatabaseKeyManager.getPassphrase(context)
val factory = SupportOpenHelperFactory(passphrase)
return Room.databaseBuilder(context, AppDatabase::class.java, "steps_of_babylon.db")
    .openHelperFactory(factory)
    .addMigrations(*AppMigrations.ALL)                                     // MIGRATION_7_8
    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
    .build()
```

The `.build()` call doesn't *open* the DB yet — it returns a
lazy-opening `AppDatabase`. The first DAO method that actually
executes SQL triggers `openHelper.writableDatabase`, which runs
migrations and decrypts via the passphrase.

### 2.3 `DatabaseKeyManager.getPassphrase(context)` — the Keystore dance

```kotlin
val prefs = context.getSharedPreferences("db_key_prefs", Context.MODE_PRIVATE)
val existing = prefs.getString("encrypted_passphrase", null)

if (existing != null) {
    try {
        return decrypt(
            Base64.decode(existing, NO_WRAP),
            Base64.decode(prefs.getString("passphrase_iv", "")!!, NO_WRAP),
        )                                                // AES/GCM/NoPadding with Keystore key
    } catch (e: Exception) {
        Log.w(TAG, "Passphrase decryption failed, generating fresh key", e)
        prefs.edit().clear().apply()
    }
}

// First-run or fallback path
val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }     // 256 bits random
val (encrypted, iv) = encrypt(passphrase)
prefs.edit()
    .putString("encrypted_passphrase", Base64.encodeToString(encrypted, NO_WRAP))
    .putString("passphrase_iv", Base64.encodeToString(iv, NO_WRAP))
    .apply()
return passphrase
```

Inside `getOrCreateKeystoreKey`:

```kotlin
val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
ks.getEntry("steps_of_babylon_db_key", null)?.let {
    return (it as KeyStore.SecretKeyEntry).secretKey
}
// First-run: generate a new AES-256-GCM key rooted in the Android Keystore
val spec = KeyGenParameterSpec.Builder(alias,
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(BLOCK_MODE_GCM)
    .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .build()
return KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore").run {
    init(spec); generateKey()
}
```

### 2.4 First DAO access

For example, `playerRepository.observeProfile()` from `HomeViewModel.init`:

```
observeProfile()
  → dao.get()                        [Flow<PlayerProfileEntity?>]
      → room writeableDatabase        [opens SQLCipher file steps_of_babylon.db]
          → SupportOpenHelperFactory applies passphrase during open
          → Room runs schema migrations: MIGRATION_7_8 if coming from v7
          → Executes SELECT query
      → emits Flow
  → filterNotNull().map { toDomain() }
```

`MIGRATION_7_8` runs a single `ALTER TABLE` to add the
`battleStepsEarned` column:

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE daily_step_record " +
            "ADD COLUMN battleStepsEarned INTEGER NOT NULL DEFAULT 0"
        )
    }
}
```

## 3. Resource Management

| Concern | How |
|---|---|
| Native lib | `System.loadLibrary("sqlcipher")` in `onCreate`. Must precede any SQLCipher-using code path. |
| Hilt singletons | `@Singleton` on `provideDatabase`; one `AppDatabase` per process. DAO providers are not `@Singleton` — they return `db.xxxDao()` on demand (Room caches DAO internally). |
| Keystore key | One `"steps_of_babylon_db_key"` alias, reused forever. GCM with `setKeySize(256)`. |
| SharedPreferences | `db_key_prefs` — one file, two keys (`encrypted_passphrase`, `passphrase_iv`). |
| Schema export | `app/schemas/*.json` — committed; 1.json through 8.json. |
| Migration list | `AppMigrations.ALL: Array<Migration>` = `[MIGRATION_7_8]`. Downgrades destroy tables (`fallbackToDestructiveMigrationOnDowngrade(true)`). |
| Passphrase lifetime | Returned as `ByteArray` from `getPassphrase`. Passed into `SupportOpenHelperFactory(passphrase)` which holds it internally. The bytes are not zeroed out in Java after use; GC'd eventually. |

## 4. Error Path

- **`System.loadLibrary("sqlcipher")` fails** (corrupt APK,
  architecture mismatch) — throws `UnsatisfiedLinkError`. Caught by
  Android framework → `onCreate` fails → process crash at startup.
  User sees a blank relaunch screen. No silent fallback.
- **Keystore decryption fails** (device restore, factory reset, key
  wiped by OEM):
  - Caught in `getPassphrase`'s try/catch.
  - `prefs.edit().clear().apply()` wipes the stored encrypted
    passphrase and IV.
  - Fresh 32-byte passphrase is generated.
  - Fresh Keystore key is created (or reused if still present).
  - **Consequence**: the existing encrypted SQLCipher database
    becomes **unreadable** — because the *old* passphrase is now
    lost, not the current one (the current one was just generated).
    On next Room access, SQLCipher will attempt to open
    `steps_of_babylon.db` with the new passphrase and fail.
    `SupportOpenHelperFactory` will throw, which Room will propagate
    as a crash or as a migration failure.
  - In practice `fallbackToDestructiveMigrationOnDowngrade` does NOT
    cover this scenario (that's for schema downgrades only). There
    is no `fallbackToDestructiveMigration()`. So the app crashes
    with an unrecoverable DB error. This is effectively a device
    restore = wipe state bug. See §9.
- **Migration fails** — `Migration.migrate` throws. Room treats this
  as a fatal error; the DB does not open. Crash.
- **DB file locked / corrupted** — SQLCipher throws during open.
  Crash.
- **SharedPreferences write fails** (very rare) — `apply()`
  swallows, next read returns null, first-run path triggers again
  → new passphrase generated → old DB unreadable (same as above).

## 5. Performance Characteristics

- `loadLibrary` ≈ 10-30 ms (JNI + dlopen of `libsqlcipher.so`).
- First `getPassphrase`: ~5-15 ms (Keystore IPC + AES/GCM decrypt
  of 48 bytes). Subsequent calls would also be fast but happen only
  once per process.
- `KeyStore.getInstance("AndroidKeyStore")` — ~1 ms.
- `Room.databaseBuilder(...).build()` — lazy, nearly free.
- First DAO call — migration + initial SELECT. Migration is one
  ALTER TABLE on a small table; < 10 ms. SELECT is microseconds.
- Subsequent DAO calls — standard Room perf.

On a cold start (process just created), total DB-bootstrap overhead
on the critical path to first query: ~20-50 ms. This is front-loaded
but only runs once per process.

## 6. Observable Effects

- **SharedPreferences `db_key_prefs`**:
  - First run: two new keys written (`encrypted_passphrase`,
    `passphrase_iv`).
  - Fallback: both wiped, then re-written.
- **Android Keystore**: one entry `steps_of_babylon_db_key` created
  on first run. Visible (to privileged users) via
  `KeyStore.getInstance("AndroidKeyStore").aliases()`.
- **File system**: `steps_of_babylon.db` created in the app's
  internal files dir (SQLCipher-encrypted page format). First few
  KB after fresh install.
- **Schema export**: at dev time, `app/schemas/8.json` is written by
  the Room compiler. At runtime, no schema export.
- **Log** entry: `Log.w("DatabaseKeyManager", "Passphrase decryption
  failed, ...")` only on fallback path.
- **WorkManager**: `step_sync` periodic request enqueued with KEEP
  policy.

Nothing user-visible unless the fallback path fires.

## 7. Why This Design

- **SQLCipher for at-rest encryption** — the threat model is a
  cloned-APK restore on another device, adb pull of the DB file on
  a rooted device, or a forensic image. Keystore-bound key means
  the passphrase never exists outside the app process in plain
  form.
- **Per-device key, not a static app secret** — prevents "one key
  compromises all installs".
- **AES-GCM for the passphrase blob** — authenticated encryption
  (tamper-evident IV+tag). If someone edits the blob, decryption
  fails and the fallback path fires.
- **Deep fallback (wipe prefs, regenerate key)** rather than crash
  or prompt — because on a legitimate device-restore scenario,
  there's nothing to prompt for; the user expects a fresh DB.
- **Migration `7 → 8` is destructive-downgrade, explicit-upgrade** —
  matches the project's practice of never dropping user data on a
  version bump.
- **`System.loadLibrary` before anything else** because a late load
  breaks the `SupportOpenHelperFactory` with no good error.
- **Passphrase as `ByteArray`** (not `char[]`) because
  `SupportOpenHelperFactory` takes byte[]. Kotlin doesn't have a
  native zeroable primitive, so this is effectively unzeroable.

## 8. Feels Incomplete

- **No migration test** in the JVM suite. `RoomSchemaTest` checks
  v8 round-trip; `MIGRATION_7_8` itself isn't exercised by a
  migration test. `MigrationTestHelper` would need
  androidTest infrastructure.
- **No "broken state" recovery**. If the passphrase decrypt fails,
  the key wipe happens but the DB file is left on disk. On next
  open attempt, SQLCipher will fail to decrypt with the new
  passphrase. The app has no retry-with-wipe flow. See §9.
- **Passphrase is not zeroed** after use. A memory snapshot could
  extract it. Mitigations in the spec: the whole DB is encrypted,
  so even with the passphrase someone needs the file.
- **No pepper / key rotation mechanism**. If the Keystore key is
  compromised (unlikely), there's no built-in path to rotate.
- **SharedPreferences for encrypted blob** is fine but leaks
  metadata: file size and mtime changes on rotation.

## 9. Feels Vulnerable

- **Device-restore-kills-database bug**: the fallback in
  `getPassphrase` wipes the old passphrase and generates a new
  one, but the existing DB file still exists and can't be
  decrypted with the new passphrase. Next Room access crashes.
  The user would see repeated crashes on launch until the OS
  clears app data (or a reinstall). This should be paired with
  `fallbackToDestructiveMigration()` **or** an explicit "wipe DB
  file on decrypt failure" step inside `DatabaseKeyManager`.
- **Key alias collision**: if another app on the device used the
  same alias (impossible by Keystore design — aliases are
  per-app), this would be an issue. Not a real vulnerability.
- **Startup ordering**: `System.loadLibrary` runs before Hilt
  initialisation completes. Hilt's `@HiltAndroidApp`-generated
  `onCreate` calls `super.onCreate()` which triggers Hilt's
  content provider. If another ContentProvider initialises faster
  and tries to open the DB before `loadLibrary` runs, SQLCipher
  JNI will fail. In this codebase there are no other content
  providers, so safe.
- **No integrity check** of the SharedPreferences file. An attacker
  with write access could overwrite the encrypted passphrase with
  garbage, causing the wipe path (which is arguably the desired
  behaviour). Could overwrite with *another app's* encrypted
  blob to confuse. Again, Android sandboxing makes this
  unreachable without root.
- **Passphrase regeneration races with another process
  holding the DB open** — not applicable here (single-process app).
- **`SecureRandom()` without explicit seeding** — relies on OS
  entropy. On embedded / emulated devices with low entropy this
  can be slow (first call). Has not been an issue in practice.

## 10. Feels Like Bad Design

- **DB passphrase passed as a bare `ByteArray`** through the DI
  graph into `SupportOpenHelperFactory`. No wrapper, no
  zero-on-close semantics. A dedicated `DatabasePassphrase` class
  owning the byte array with `AutoCloseable` semantics would be
  safer.
- **Two independent init steps** (`loadLibrary` + `DatabaseKeyManager`)
  with an implicit ordering contract: `loadLibrary` must happen
  before Room. Documented only as a comment in `StepsOfBabylonApp`
  (not visible in current file — if it existed there'd be less
  drift risk). A `DatabaseBootstrapper` that enforces the order
  would be clearer.
- **`getPassphrase`'s exception handling** wipes prefs on *any*
  exception, including benign transient Keystore errors (e.g. the
  key is temporarily locked behind user authentication in future).
  An unconditional wipe is a lot of data loss to trade for
  simplicity.
- **Nothing observable** — no metric, no log, no debug surface —
  telling operators whether the fallback path has ever fired on a
  device in the wild. "My game forgot my progress" could be this
  bug, but there's no trail to confirm.
- **Schema version and file name** are spread between
  `AppDatabase` (`version = 8`), `DatabaseModule` (`"steps_of_babylon.db"`),
  and `AppMigrations.ALL` (must include `MIGRATION_7_8`). A
  single `DatabaseSchema` const object listing all four things
  would help future migrations.
- **`HealthConnectModule` is an intentional empty `@Module`** just
  for organisational purposes (all HC classes use `@Inject
  constructor`). This is fine, but the module's Kdoc says
  "organisational placeholder" — which is accurate but invites
  a future engineer to "just put something here" (violating the
  convention that HC bindings are constructor-driven).
