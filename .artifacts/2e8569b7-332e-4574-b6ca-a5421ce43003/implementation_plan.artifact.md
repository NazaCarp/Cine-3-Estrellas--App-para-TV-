# Creación del APK Release para TV

El objetivo es generar el archivo APK de la versión de TV (módulo `:app`) configurado para producción (release).

## Consideraciones
- El módulo `:app` contiene las dependencias de Android TV (`androidx.tv.material`).
- La configuración actual en `app/build.gradle.kts` utiliza `signingConfigs.debug` para el tipo de compilación `release`. Esto generará un APK funcional pero firmado con la clave de depuración.

## Propuestas de Cambio
No se requieren cambios en el código, solo la ejecución de la tarea de Gradle.

## Plan de Verificación

### Pruebas Automatizadas
- Ejecutar `./gradlew :app:assembleRelease` para asegurar que el proceso de compilación y minificación (R8) se complete correctamente.

### Verificación Manual
- Localizar el archivo APK generado en `app/build/outputs/apk/release/`.
