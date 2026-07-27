# Generar APK de Lanzamiento (Release)

Este plan detalla los pasos para generar el archivo APK de lanzamiento para la aplicación "Cine 3 Estrellas".

## User Review Required

> [!IMPORTANT]
> **Configuración de Firma (Signing Config):**
> Actualmente, el archivo `app/build.gradle.kts` está configurado para usar la clave de depuración (`debug`) incluso para la variante de lanzamiento (`release`):
> ```kotlin
> signingConfig = signingConfigs.getByName("debug")
> ```
> Esto generará un APK optimizado (con ProGuard/R8) pero **firmado con la clave de depuración**. Esto es aceptable para pruebas locales, pero no para publicar en Google Play Store. Si desea usar una clave de lanzamiento específica, por favor proporcione los detalles del keystore.

## Proposed Changes

No se requieren cambios en el código fuente para realizar la compilación con la configuración actual.

### Gradle Tasks

Se ejecutará la tarea de Gradle para ensamblar la variante de lanzamiento:

- `app:assembleRelease`

## Verification Plan

### Manual Verification
- Verificar la existencia del archivo APK en: `app/build/outputs/apk/release/app-release.apk`.
- Proporcionar la ruta completa al usuario para su descarga/uso.
