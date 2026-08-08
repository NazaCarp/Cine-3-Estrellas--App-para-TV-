# Plan de Implementación: Adaptación de "Cine 3 Estrellas" para Dispositivos Móviles

Este plan detalla los pasos para crear una versión móvil de la aplicación "Cine 3 Estrellas", adaptando la interfaz de usuario de TV (optimizada para control remoto y pantallas grandes) a una interfaz táctil optimizada para celulares.

## User Review Required

> [!IMPORTANT]
> Se creará un nuevo proyecto en `C:\Users\NazaCarp\OneDrive\Documentos\Programacion\Cine 3 Estrellas (App para Celular)`.
> La navegación cambiará de una barra lateral (Sidebar) a una barra de navegación inferior (Bottom Navigation) estándar en Android.
> Se eliminará toda la lógica de gestión de foco (D-pad) para simplificar el código y mejorar el rendimiento en dispositivos táctiles.

## Cambios Propuestos

### Estructura del Proyecto

Se creará una copia del proyecto base pero con las siguientes modificaciones estructurales:

1.  **Dependencias**: Se eliminarán las librerías `androidx.tv:tv-foundation` y `androidx.tv:tv-material`. Se usará `androidx.compose.material3` para todos los componentes de UI.
2.  **Configuración de Build**: Se ajustará `build.gradle.kts` para eliminar las dependencias de TV y asegurar la compatibilidad con dispositivos móviles.

### Adaptación de UI (Jetpack Compose)

#### [MODIFY] `MainActivity.kt`
- Reemplazar `MainScreen` con una implementación que use `Scaffold` y `NavigationBar`.
- Eliminar `LocalTabFocusRequesters`, `LocalTabContentFocusRequesters` y toda la lógica de `FocusRequester`.
- Simplificar los estados de overlay (detalles, categorías, historial) para que funcionen mejor con la navegación táctil y el botón de "atrás" del sistema.

#### [MODIFY] `HomeScreen.kt` y otras pantallas
- Reemplazar componentes de TV (`Surface`, `Button`, `Icon` de `androidx.tv.material3`) por sus contrapartes estándar en `androidx.compose.material3`.
- Cambiar el diseño de `HeroSection`: de un banner ancho a uno más vertical u optimizado para scroll táctil.
- Las filas de películas (`LazyRow`) se mantendrán, pero sin la lógica de escala por foco. La interacción será por toque directo.

#### [MODIFY] `Components.kt`
- Rediseñar `MovieCard` para que sea táctil.
- Eliminar `MovieCardPlaceholder` y `SeeMoreCard` que dependen de estados de foco de TV.

### Lógica de Negocio y Datos
- **Sin cambios significativos**: Archivos como `Network.kt`, `Models.kt`, `DataCache.kt`, `OkExtractor.kt`, `StreamExtractor.kt`, `TelegramManager.kt` y `SupabaseManager` se copiarán tal cual, ya que la lógica de backend y extracción de streaming es independiente del factor de forma.

## Plan de Verificación

### Pruebas Manuales
- Verificar que la aplicación inicie correctamente en un emulador o dispositivo móvil.
- Comprobar que la navegación inferior funcione y cambie entre pestañas.
- Verificar que el scroll táctil en las listas de películas sea fluido.
- Asegurar que al tocar una película se abran correctamente los detalles y el reproductor de video.
- Validar el inicio de sesión y la persistencia de datos.
