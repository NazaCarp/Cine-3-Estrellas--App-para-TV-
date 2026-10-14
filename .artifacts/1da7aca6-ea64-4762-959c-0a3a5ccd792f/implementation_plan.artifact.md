# Plan to Fix Compilation Errors and Build Release APK

The goal is to resolve the unresolved references in `MainActivity.kt` that are preventing the release APK from being built, and then successfully generate the `.apk` file for the TV version.

## User Review Required

> [!IMPORTANT]
> The release build currently uses the **debug signing configuration**. This is suitable for testing but not for publishing to the Play Store.

## Proposed Changes

### [app module]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/NazaCarp/OneDrive/Documentos/Programacion/Cine%203%20Estrellas%20(App%20para%20TV)/app/src/main/java/com/cine3estrellas/MainActivity.kt)
- Define `isTv` using the `isTvDevice` helper function.
- Define `currentRoute` using `navController.currentBackStackEntryAsState()`.
- Define `isPlayerOverlayActive` as a derived state from `playerMovieId`.

## Verification Plan

### Automated Tests
- Run `:app:assembleRelease` to verify the build completes successfully.
- Check the output directory `app/build/outputs/apk/release/` for the generated APK.

### Manual Verification
- N/A (User will be notified of the APK location).
