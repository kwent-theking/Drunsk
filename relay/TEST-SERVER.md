# Drunsk test server / тестовый сервер

Изолированный smoke-компонент протокола без запуска Minecraft, без базы данных и без чтения файлов. Он слушает только `127.0.0.1:8791` и путь `/drunsk-test`.

## Запуск

```powershell
cd C:\Users\kwent\Drunsk\relay
npm run test:server
# в другом окне
npm run test:protocol
```

Handshake требует:

- challenge из server-first `hello_challenge` (одноразовый nonce);
- `mcVersion: "26.2"`;
- безопасный ник и версию Drunsk client;
- явный объект `sodium: { present: boolean, version?: string|null }`.

Компонент **не принимает токены и не считает presence доказательством подлинности**: это локальный контрактный тест. Боевой handshake и авторизация остаются в `server.js`. `chat_send` возвращает детерминированный результат чата и отражает Sodium presence. Невалидный JSON, challenge, текст и повторный handshake отклоняются.

The component is intentionally local-only and in-memory. It does not inspect Minecraft, the filesystem, or production database. Production authentication remains in `server.js`.
