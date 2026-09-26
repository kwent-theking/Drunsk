# -*- coding: utf-8 -*-
"""Patch Drunbot main.py: add !паспорт command for Drunsk mod pairing.
Патч main.py Друнбота: команда !паспорт для привязки мода Drunsk.

Idempotent: refuses if the marker is already present. Preserves CRLF.
Идемпотентен: отказывается, если маркер уже есть. Сохраняет CRLF.
"""
import sys

SRC = sys.argv[1] if len(sys.argv) > 1 else 'main.py'
MARKER = '# === DRUNSK MOD PASSPORT PAIRING ==='

BLOCK = '''
%s
# Команда подтверждения привязки: игрок жмёт P в Minecraft, получает
# 6-значный код и вводит его здесь. Код одноразовый (10 минут), релей
# держит его sha256 в drunsk_pair_codes и сам проставляет user_id... точнее,
# мы проставляем user_id, а релей подхватывает и выдаёт токен моду.
DRUNSK_GUILD_ID = MAIN_GUILD_ID


@bot.command(name='паспорт')
async def drunsk_passport_command(ctx, code: str = None):
    """Привязать аккаунт Minecraft (код из мода Drunsk) или показать статус."""
    member = clan_member(ctx.author.id)
    if member is None:
        return await private_reply(ctx, discord.Embed(
            title='Только для клана',
            description='Паспорт выдают участникам с ролью «Паспорт».',
            color=0xE74C3C))

    if code is None:
        cursor.execute('SELECT mc_nick FROM drunsk_passports '
                       'WHERE guild_id = ? AND user_id = ?',
                       (DRUNSK_GUILD_ID, ctx.author.id))
        row = cursor.fetchone()
        if row is None:
            return await private_reply(ctx, discord.Embed(
                description='Аккаунт Minecraft ещё не привязан. В игре нажми '
                            '**P** → «Привязать аккаунт» и введи сюда код: '
                            '`!паспорт 123456`',
                color=0xF0B232))
        return await private_reply(ctx, discord.Embed(
            description='Привязан ник **%%s**. Чтобы сменить: `!паспорт сброс`.' %% row[0],
            color=0x2ECC71))

    code = code.strip().lower()
    if code in ('сброс', 'reset'):
        cursor.execute('SELECT mc_nick FROM drunsk_passports '
                       'WHERE guild_id = ? AND user_id = ?',
                       (DRUNSK_GUILD_ID, ctx.author.id))
        row = cursor.fetchone()
        if row is None:
            return await private_reply(ctx, discord.Embed(
                description='Привязки нет — сбрасывать нечего.', color=0xF0B232))
        nick = row[0]
        cursor.execute('DELETE FROM drunsk_passports '
                       'WHERE guild_id = ? AND user_id = ?',
                       (DRUNSK_GUILD_ID, ctx.author.id))
        db.commit()
        return await private_reply(ctx, discord.Embed(
            description='Привязка **%%s** снята. Токен мода больше не работает.' %% nick,
            color=0xE74C3C))

    if not (code.isdigit() and len(code) == 6):
        return await private_reply(ctx, discord.Embed(
            description='Код — ровно 6 цифр, как в игре.', color=0xE74C3C))

    now_ms = int(time.time() * 1000)
    cursor.execute('SELECT mc_nick, expires_at FROM drunsk_pair_codes '
                   'WHERE guild_id = ? AND code_hash = ? AND user_id IS NULL '
                   'LIMIT 1',
                   (DRUNSK_GUILD_ID, hashlib.sha256(code.encode('utf-8')).hexdigest()))
    row = cursor.fetchone()
    if row is None or int(row[1]) < now_ms:
        return await private_reply(ctx, discord.Embed(
            description='Код не найден или истёк. Запроси новый в игре (P → Привязка).',
            color=0xE74C3C))
    mc_nick = row[0]
    # Одна привязка на Discord и на MC-ник: UPDATE ... WHERE user_id IS NULL —
    # второй ввод того же кода уже ничего не подтвердит.
    cursor.execute('UPDATE drunsk_pair_codes SET user_id = ? '
                   'WHERE guild_id = ? AND code_hash = ? AND user_id IS NULL',
                   (ctx.author.id, DRUNSK_GUILD_ID,
                    hashlib.sha256(code.encode('utf-8')).hexdigest()))
    db.commit()
    await private_reply(ctx, discord.Embed(
        title='Паспорт оформлен',
        description='Minecraft-ник **%%s** привязан к твоему Discord.\\n'
                    'Вернись в игру — окно само подтвердит привязку.' %% mc_nick,
        color=0x2ECC71))


''' % MARKER

def main():
    with open(SRC, 'rb') as f:
        data = f.read()
    text = data.decode('utf-8')
    if MARKER in text:
        print('already patched, nothing to do')
        return
    # Preserve whatever EOL the source actually uses. As of 2026-09-25 the
    # server file is LF (memory used to say CRLF — the file changed under us).
    # Сохраняем те переводы строк, что реально в файле (на 25.09 серверный LF).
    crlf = b'\r\n' in data
    if not crlf and b'\n' not in data:
        print('ERROR: no line endings found, refusing')
        sys.exit(2)
    anchor = "@bot.command(name='ботлох')"
    if anchor not in text:
        print('ERROR: anchor not found')
        sys.exit(3)
    block = BLOCK.replace('\n', '\r\n') if crlf else BLOCK
    idx = text.index(anchor)
    patched = text[:idx] + block + text[idx:]
    with open(SRC, 'wb') as f:
        f.write(patched.encode('utf-8'))
    print('patched ok, eol=%s' % ('CRLF' if crlf else 'LF'))


if __name__ == '__main__':
    main()
