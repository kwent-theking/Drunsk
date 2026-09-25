# Chekushki Mod — Minecraft 26.2 (Fabric)

Мод синхронизирует валюту «чекушки» из Discord-бота Drunsk с Minecraft.

## Функции

- открытие меню по клавише `L` (регистрируется в настройках управления)
- баланс чекушек
- перевод чекушек другому игроку
- лудка: шанс 777 задаётся в панели, выигрыш удваивает ставку
- список паспортов — доступен только игроку `_Belmo`
- подключение к API `/botpanel/mod/*`

## API

Мод использует:

- `GET /botpanel/mod/status?nick=НИК`
- `POST /botpanel/mod/transfer`
- `POST /botpanel/mod/casino`
- `GET /botpanel/mod/passports?nick=_Belmo`

## Сборка

Требуется:

- Java 25
- Gradle 9.7+
- Fabric Loom 1.18.2

```bash
gradle build
```

Готовый файл появится в:

```text
build/libs/chekushki-1.0.0+26.2.jar
```

## Статус

Проект настроен на Minecraft `26.2`, Fabric Loader `0.19.5`, Fabric API `0.161.0+26.2`.

Сборка кода зависит от публикации корректных маппингов для 26.2. Сейчас для этой версии Fabric ещё не выпустил стабильные Yarn/official mappings, поэтому рабочий `.jar` собрать нельзя.

API на VPS уже работает и проверен.
