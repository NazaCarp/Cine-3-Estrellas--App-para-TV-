# Plan para generar los APKs de Release

Este plan detalla los pasos para generar los archivos APK en modo release para los módulos del proyecto Cine 3 Estrellas.

## Consideraciones de Firma

> [!IMPORTANT]
> Actualmente, la configuración de ambos módulos (`:app` y `:app-mobile`) está establecida para firmar el build de **release** con la clave de **debug**.
>
> ```kotlin
> signingConfig = signingConfigs.getByName("debug")
> ```
>
> Esto generará un APK funcional pero no apto para subir a la Google Play Store, ya que requiere una firma de producción. Para este proceso, asumiremos que se desea generar el APK con esta configuración actual para pruebas de rendimiento o distribución manual.

## Cambios Propuestos

No se requieren cambios en el código fuente, solo la ejecución de tareas de Gradle para compilar los proyectos.

### Ejecución de Compilación

Se ejecutarán los siguientes comandos de Gradle:

1.  **Limpiar el proyecto:** `./gradlew clean` para asegurar un build limpio.
2.  **Generar APK de TV:** `./gradlew :app:assembleRelease`
3.  **Generar APK Móvil:** `./gradlew :app-mobile:assembleRelease`

## Plan de Verificación

### Verificación Manual
- Se confirmará la existencia de los archivos APK en las siguientes rutas:
    - `app/build/outputs/apk/release/app-release.apk`
    - `app-mobile/build/outputs/apk/release/app-mobile-release.apk`
