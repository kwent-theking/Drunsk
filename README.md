# Drunsk

Инфраструктура клана Друнск для Minecraft: виртуальные паспорта, общий с
Друнботом баланс чекушек, переводы и лудка.
Clan Drunsk Minecraft infrastructure: virtual passports, balance shared with
Drunbot, money transfers and casino.

В репозитории два независимых мода и серверная часть / Two independent mods and a server side:

| Путь | Что | Версия MC | Автор |
|---|---|---|---|
| `mod/` | **Drunsk passports** — паспорта, книга паспортов, переводы, лудка, история, ЛС, PvE-утилиты; связь через wss-relay | 26.2 (Fabric, unobfuscated, loom 1.17) | kwent |
| `src/`, `build.gradle` (корень) | **Chekushki** — баланс/переводы/лудка через HTTP API `/botpanel/mod/*` | 26.2 (Fabric, Java 25, loom 1.18.2) | Belmo |
| `relay/` | Relay-сервер (Node.js) на ВПС клана: мост к MySQL-экономике Друнбота | — | kwent |

Деньги ОДНИ на всех: баланс в обоих модах — это та же строка `users.balance` в
базе Друнбота (`s1_okak`), что и `!баланс` в Discord. Перевод из игры мгновенно
виден в Discord и наоборот.

---

## Часть kwent: мод `mod/` + relay

```
Minecraft (мод)  --wss-->  Caddy /drunsk*  -->  127.0.0.1:8790 drunsk-relay  -->  MariaDB s1_okak
                                                                                        ^
Discord (Друнбот: !баланс, !дать, !паспорт)  -------------------------------------------+
```

### Привязка паспорта / Passport pairing

1. В игре нажать **P** (или `/drunsk pair`) — мод покажет 6-значный код.
2. В Discord клана написать `!паспорт <код>` — релей выдаст моду токен и сохранит привязку.
3. Дальше токен живёт в `config/drunsk.json`, перепривязка не нужна.
4. `!паспорт` без кода — статус привязки, `!паспорт сброс` — снять (старый токен умирает).

### Возможности мода / Mod features

- **P** на игрока — его паспорт (ник, Discord-имя, баланс, дата выдачи, онлайн).
- **P** в никуда — хаб: мой паспорт, книга паспортов (все игроки клана),
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
- systemd: `drunsk-relay`, `Restart=always`, креды БД в `/etc/drunsk-relay.env` (600, только env — в репо не попадают).

### Сборка / Build

```
cd mod
build.bat        # кэшированный Gradle 9.6.1 + JDK 25; jar -> mod/build/libs/drunsk-0.1.0-beta.jar
```

Требования мода: Fabric Loader >= 0.18, Fabric API (обязательно), Minecraft ~26.2, Java 25+.

Selftest релея (на ВПС, только против `drunsk_test` — НЕ против живой базы!):
`cd /opt/drunsk-relay && node selftest.js` — 45 проверок: привязка, auth,
переводы (включая 10 параллельных — сохранение денег), лудка (сохранение денег,
сверка с бухгалтерией `drunsk_tx`), приватность, presence, рейт-лимит, чужой
токен, ЛС (dm_send/dm_history/валидация).

### Деплой на ВПС / VPS deploy

- Сервис: `/etc/systemd/system/drunsk-relay.service`, код в `/opt/drunsk-relay/`.
- Caddy: `handle /drunsk* { reverse_proxy 127.0.0.1:8790 }` → `wss://друнск.online/drunsk`
  (punycode: `xn--d1amilgk.online`).
- Таблицы релея в `s1_okak`: `drunsk_passports`, `drunsk_pair_codes`, `drunsk_tx`
  (DDL только по своим таблицам — на таблицах бота висит metadata lock).
- Патч бота: команда `!паспорт` в `main.py` (скрипт `scratchpad/bot/patch_passport.py`,
  идемпотентный, сохраняет EOL; бэкап `main.py.bak-drunsk-*`).

### Протокол / Protocol

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
| dm_send | to, text | at; пуш `dm` получателю (и вторым сокетам отправителя) |
| dm_history | peer | последние 100 сообщений треда, старые первыми |
| ping | — | эхо; пуши: `presence`, `balance_changed`, `paired`, `pair_expired`, `dm` |

---

## Часть Belmo: мод Chekushki (корень репо)

Мод синхронизирует валюту «чекушки» из Discord-бота Drunsk с Minecraft 26.2.

- открытие меню по клавише `L` (регистрируется в настройках управления)
- баланс чекушек
- перевод чекушек другому игроку
- лудка: шанс 777 задаётся в панели, выигрыш удваивает ставку
- список паспортов — доступен только игроку `_Belmo`
- подключение к API `/botpanel/mod/*`:
  - `GET /botpanel/mod/status?nick=НИК`
  - `POST /botpanel/mod/transfer`
  - `POST /botpanel/mod/casino`
  - `GET /botpanel/mod/passports?nick=_Belmo`

Сборка: Java 25, Gradle 9.7+, Fabric Loom 1.18.2; `gradle build` →
`build/libs/chekushki-1.0.0+26.2.jar`. Подробности в `SETUP.md` и `SECURITY.md`.
