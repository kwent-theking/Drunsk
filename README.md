# Drunsk

Виртуальные паспорта, общий баланс с Друнботом, переводы и лудка для клана Друнск.
Virtual passports, shared balance with Drunbot, money transfers and casino for the Drunsk clan.

Состоит из двух частей / Two parts:

- `mod/` — клиентский Fabric-мод для Minecraft 1.21.11 (mojmap, loom 1.13, Java 21+).
- `relay/` — Node.js relay-сервер на ВПС `31.77.147.126`: мост к MySQL-экономике
  Друнбота (`s1_okak`), хранит привязки паспортов, крутит лудку, ведёт историю операций.

## Как это работает / How it works

```
Minecraft (мод)  --wss-->  Caddy /drunsk*  -->  127.0.0.1:8790 drunsk-relay  -->  MariaDB s1_okak
                                                                                        ^
Discord (Друнбот: !баланс, !дать, !паспорт)  -------------------------------------------+
```

Деньги ОДНИ: баланс в моде — это та же строка `users.balance`, что и `!баланс` в
Discord. Перевод из игры мгновенно виден в Discord и наоборот.
Money is SHARED: the mod's balance is the same `users.balance` row as Discord's
`!баланс`. Transfers are visible on both sides immediately.

### Привязка паспорта / Passport pairing

1. В игре нажать **P** (или `/drunsk pair`) — мод покажет 6-значный код.
2. В Discord клана написать `!паспорт <код>` — релей выдаст моду токен и сохранит привязку.
3. Дальше токен живёт в `config/drunsk.json`, перепривязка не нужна.
4. `!паспорт` без кода — статус привязки, `!паспорт сброс` — снять (старый токен умирает).

### Возможности мода / Mod features

- **P** на игрока — его паспорт (ник, Discord-имя, баланс, дата выдачи, онлайн).
- **P** в никуда — своё меню (хаб): мой паспорт, книга паспортов (все игроки клана),
  перевод, лудка, история операций.
- Переводы: атомарно в БД, с проверкой средств; получатель получает live-уведомление.
- Лудка (бросок на сервере): монетка x2 (48%), кости больше/меньше x2 (49%),
  рулетка красное/чёрное x2 и точное число 0..14 x14. Ставки 1..100 000.
- Флаги приватности Друнбота уважаются: `hide_balance` скрывает сумму,
  `is_hidden` убирает паспорт из чужих списков. Владельцы (kwent, Belmo) видят всё.

### Стабильное подключение / Stable connection

- Автопереподключение с экспоненциальным backoff (1 с → 30 с), heartbeat ping/pong.
- Токен-бакет рейт-лимита (30/с) — ответ об ограничении несёт id запроса, клиент не виснет.
- 64-битные Discord ID передаются строками (`bigNumberStrings`) — JS-double теряет точность.
- Relay слушает только 127.0.0.1, наружу — только через Caddy (wss).
- systemd: `drunsk-relay`, `Restart=always`, креды БД в `/etc/drunsk-relay.env` (root:drunskrelay, 600).

## Сборка / Build

Мод / Mod:

```
cd mod
build.bat        # cached Gradle 9.6.1 + JDK 23; jar lands in mod/build/libs/drunsk-1.0.0.jar
```

Требования мода: Fabric Loader >= 0.19, Fabric API (обязательно), Minecraft ~1.21.11, Java 21+.

Relay selftest (на ВПС, против `drunsk_test` — НЕ против живой базы!):

```
ssh root@31.77.147.126
cd /opt/drunsk-relay
DB_HOST=127.0.0.1 DB_NAME=drunsk_test ... node selftest.js   # creds via env from /etc/drunsk-relay.env
```

37 проверок: привязка, auth, переводы (включая 10 параллельных — сохранение денег),
лудка (сохранение денег, сверка с бухгалтерией drunsk_tx), приватность, presence,
рейт-лимит, чужой токен.

## Деплой на ВПС / VPS deploy

- Сервис: `/etc/systemd/system/drunsk-relay.service`, код в `/opt/drunsk-relay/`.
- Caddy: `handle /drunsk* { reverse_proxy 127.0.0.1:8790 }` → `wss://xn--d1amilgk.online/drunsk`
  (друнск.online; `31.77.147.126.sslip.io` тоже работает).
- Таблицы релея в `s1_okak`: `drunsk_passports`, `drunsk_pair_codes`, `drunsk_tx`
  (DDL только по своим таблицам — на таблицах бота висит metadata lock).
- Патч бота: команда `!паспорт` в `main.py` (скрипт `scratchpad/bot/patch_passport.py`,
  идемпотентный, сохраняет EOL; бэкап `main.py.bak-drunsk-*`).

## Протокол / Protocol

Клиент → сервер: `{id, type, ...}`. Сервер → клиент: `{id, ok, ...}` или пуш `{type}`.

| type | поля | ответ |
|---|---|---|
| hello | mcNick, token | userId, owner, currency |
| pair_request | mcNick | code (6 цифр), затем пуш `paired` с token |
| me | — | баланс, ник, discordName, since, owner |
| list | — | паспорта клана (онлайн, скрытость) |
| passport | mcNick? | чужой/свой паспорт |
| transfer | toNick, amount | newBalance; пуш `balance_changed` получателю |
| casino | game, bet, pick? | win, payout, detail, newBalance |
| history | limit? | последние операции |
| ping | — | эхо; пуши: `presence`, `balance_changed`, `paired`, `pair_expired` |
