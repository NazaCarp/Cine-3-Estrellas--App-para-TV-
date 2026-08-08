# Solución para Limpieza de Códigos de Autenticación Expirados

La acumulación de registros expirados en la tabla `users` (columnas `auth_code` y `auth_code_expires_at`) puede causar colisiones si el sistema de generación de códigos no verifica si un código existente ya ha caducado.

## Propuesta de Solución

La solución más eficiente y robusta es implementar una **limpieza automática a nivel de base de datos** utilizando las capacidades nativas de Supabase (PostgreSQL).

### 1. Limpieza Programada con `pg_cron` (Recomendado)

Podemos programar un "trabajo" (cron job) que se ejecute cada cierto tiempo (ej. cada 30 minutos) y limpie todos los códigos que hayan superado su fecha de expiración.

#### Pasos en el Dashboard de Supabase:
1. Ir a **SQL Editor**.
2. Ejecutar el siguiente script:

```sql
-- Habilitar la extensión pg_cron si no está activa
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- Crear una función que realice la limpieza
CREATE OR REPLACE FUNCTION clean_expired_auth_codes()
RETURNS void AS $$
BEGIN
  UPDATE users
  SET auth_code = NULL,
      auth_code_expires_at = NULL
  WHERE auth_code_expires_at < NOW();
END;
$$ LANGUAGE plpgsql;

-- Programar la función para que corra cada 30 minutos
-- '*/30 * * * *' significa "cada 30 minutos"
SELECT cron.schedule('limpiar-codigos-expirados', '*/30 * * * *', 'SELECT clean_expired_auth_codes()');
```

> [!TIP]
> Esto asegura que la base de datos se mantenga limpia sin necesidad de que el bot o la app hagan trabajo extra.

---

### 2. Mejora en la Lógica de Generación (En el Bot de Telegram)

Si tienes acceso al código del Bot de Telegram que genera los códigos, te recomiendo que antes de generar un código nuevo para un usuario, limpies su código anterior si ya expiró:

**Lógica recomendada para el Bot:**
1. El usuario pide un código.
2. El bot ejecuta:
   `UPDATE users SET auth_code = NULL, auth_code_expires_at = NULL WHERE id = ? AND auth_code_expires_at < NOW()`
3. El bot genera el nuevo código y lo guarda.

---

### 3. Solución de "Bajo Costo" (Cleanup al Consultar)

Si no quieres usar `pg_cron`, puedes modificar el bot para que cuando busque si un código está disponible, ignore los que ya expiraron.

**Consulta SQL para el Bot:**
```sql
-- En lugar de buscar si existe el código simplemente:
SELECT id FROM users WHERE auth_code = '123456';

-- Debería buscar si existe un código ACTIVO:
SELECT id FROM users WHERE auth_code = '123456' AND auth_code_expires_at > NOW();
```
Si la consulta no devuelve nada, el código '123456' está libre (o su poseedor actual ya lo tiene expirado y puede ser sobrescrito).

## Recomendación Final

La **Opción 1 (pg_cron)** es la mejor porque previene que la tabla se llene de basura y reduce drásticamente la probabilidad de colisiones, independientemente de cómo esté programado el bot.

## Plan de Verificación

1. **Manual:** Ejecutar la función `SELECT clean_expired_auth_codes();` manualmente en el SQL Editor y verificar que los registros con fecha pasada se pongan en `NULL`.
2. **Automático:** Esperar el intervalo del cron y revisar la tabla `users`.
