# Plan de Implementación: Fijar Foco en el Botón "OBTENER ENLACES"

El objetivo es evitar que el foco se pierda (o salte a elementos no deseados) cuando el usuario presiona hacia abajo desde el último elemento de las opciones de reproducción, que es el botón "OBTENER ENLACES".

## Cambios Propuestos

### [Componente de UI]

Se modificará el archivo `MovieDetailsScreen.kt` para restringir el movimiento del foco hacia abajo en el botón de descarga.

#### [MODIFY] [MovieDetailsScreen.kt](file:///C:/Users/NazaCarp/OneDrive/Documentos/Programacion/Cine%203%20Estrellas%20(App%20para%20TV)/app/src/main/java/com/cine3estrellas/screens/MovieDetailsScreen.kt)

- Localizar el `Surface` que contiene el texto "OBTENER ENLACES".
- Añadir `.focusProperties { down = FocusRequester.Cancel }` al `Modifier` de dicho `Surface`. Esto asegura que si no hay nada más abajo, el foco permanezca en el botón actual en lugar de intentar buscar otro elemento fuera del contenedor actual.

## Plan de Verificación

### Verificación Manual
- Navegar a la pantalla de detalles de una película.
- Abrir las opciones de reproducción.
- Desplazarse hasta el botón "OBTENER ENLACES".
- Presionar la tecla "Abajo" en el control remoto/teclado.
- Confirmar que el foco permanece en el botón "OBTENER ENLACES" y no desaparece ni salta a otro lugar.
