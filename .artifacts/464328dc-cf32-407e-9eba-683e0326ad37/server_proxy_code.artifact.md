# Código del Servidor Proxy (Vercel)

Debes crear estos archivos en tu proyecto de Vercel (Next.js) dentro de la carpeta `pages/api/telegram/`.

> [!IMPORTANT]
> **Variables de Entorno:**
> Configura estas variables en el panel de Vercel (Settings -> Environment Variables):
> - `TELEGRAM_BOT_TOKEN`: Tu nuevo token del bot principal.
> - `TELEGRAM_VERIFY_TOKEN`: Tu nuevo token del bot de verificación.
> - `TELEGRAM_GROUP_ID`: `@Cine_3Estrellas`

---

## 1. Membership Check (`pages/api/telegram/membership.js`)

```javascript
export default async function handler(req, res) {
  const { user_id } = req.query;
  const token = process.env.TELEGRAM_VERIFY_TOKEN;
  const groupId = process.env.TELEGRAM_GROUP_ID;

  if (!user_id) return res.status(400).json({ ok: false, description: "Missing user_id" });

  try {
    const response = await fetch(`https://api.telegram.org/bot${token}/getChatMember?chat_id=${groupId}&user_id=${user_id}`);
    const data = await response.json();
    res.status(200).json(data);
  } catch (error) {
    res.status(500).json({ ok: false, description: error.message });
  }
}
```

---

## 2. User Info (`pages/api/telegram/user-info.js`)

```javascript
export default async function handler(req, res) {
  const { telegram_id } = req.query;
  const token = process.env.TELEGRAM_BOT_TOKEN;

  if (!telegram_id) return res.status(400).json({ ok: false, description: "Missing telegram_id" });

  try {
    const response = await fetch(`https://api.telegram.org/bot${token}/getChat?chat_id=${telegram_id}`);
    const data = await response.json();
    res.status(200).json(data);
  } catch (error) {
    res.status(500).json({ ok: false, description: error.message });
  }
}
```
