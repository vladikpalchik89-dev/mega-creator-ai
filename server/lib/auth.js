'use strict';

const crypto = require('crypto');
const { config } = require('./config');
const store = require('./store');
const { fail, sha256, randomId, nowIso, today, clip } = require('./util');

const CODE_TTL_MS = 10 * 60 * 1000;
const TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const MAX_CODE_ATTEMPTS = 5;

function normalizeContact(body) {
  const rawPhone = typeof body.phone === 'string' || typeof body.phone === 'number' ? String(body.phone) : '';
  const phone = rawPhone.replace(/\D/g, '');
  const email = typeof body.email === 'string' ? body.email.trim().toLowerCase() : '';

  if (!phone && !email) fail(400, 'Укажите телефон или e-mail');
  if (phone && (phone.length < 7 || phone.length > 15)) fail(400, 'Некорректный номер телефона');
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) fail(400, 'Некорректный e-mail');

  return {
    phone: phone || null,
    email: email || null,
    to: phone || email,
    channel: phone ? 'sms' : 'email'
  };
}

function findUser(contact) {
  const byPhone = contact.phone
    ? store.find('users', (user) => user.phone === contact.phone && !user.deletedAt)
    : null;
  const byEmail = contact.email
    ? store.find('users', (user) => user.email === contact.email && !user.deletedAt)
    : null;
  if (byPhone && byEmail && byPhone.id !== byEmail.id) {
    fail(400, 'Телефон и e-mail относятся к разным аккаунтам');
  }
  return byPhone || byEmail || null;
}

function publicUser(user) {
  return {
    id: user.id,
    name: user.name || null,
    phone: user.phone || null,
    email: user.email || null,
    tariff: user.tariff || 'FREE'
  };
}

async function register(body) {
  const contact = normalizeContact(body);

  if (!config.codeWebhookUrl && !config.devMode) {
    fail(501, 'Отправка кодов не настроена: укажите CODE_WEBHOOK_URL или включите DEV_MODE=true');
  }

  let user = findUser(contact);
  if (!user) {
    user = store.insert('users', {
      id: randomId(),
      phone: contact.phone,
      email: contact.email,
      name: clip(body.name, 80) || null,
      tariff: 'FREE',
      role: 'user',
      createdAt: nowIso(),
      deletedAt: null
    });
  } else {
    const patch = {};
    if (!user.phone && contact.phone) patch.phone = contact.phone;
    if (!user.email && contact.email) patch.email = contact.email;
    if (!user.name && body.name) patch.name = clip(body.name, 80) || null;
    if (Object.keys(patch).length > 0) user = store.update('users', user.id, patch);
  }

  const code = String(crypto.randomInt(0, 1000000)).padStart(6, '0');
  // Выдача нового кода делает предыдущие коды для этого контакта недействительными.
  for (const previous of store.filter('codes', (row) => row.to === contact.to && !row.used)) {
    store.update('codes', previous.id, { used: true });
  }
  store.insert('codes', {
    id: randomId(),
    userId: user.id,
    to: contact.to,
    channel: contact.channel,
    hash: sha256(code),
    expiresAt: new Date(Date.now() + CODE_TTL_MS).toISOString(),
    used: false,
    attempts: 0,
    createdAt: nowIso()
  });

  if (config.devMode) {
    return {
      ok: true,
      message: 'Код создан в тестовом режиме (DEV_MODE)',
      devCode: code,
      channel: contact.channel,
      to: contact.to,
      expiresIn: 600
    };
  }

  try {
    const response = await fetch(config.codeWebhookUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ to: contact.to, code: code, channel: contact.channel }),
      signal: AbortSignal.timeout(30000)
    });
    if (!response.ok) fail(502, 'Не удалось отправить код: сервис доставки вернул ошибку');
  } catch (error) {
    if (error && error.name === 'HttpError') throw error;
    fail(502, 'Не удалось отправить код: сервис доставки недоступен');
  }

  return { ok: true, message: 'Код отправлен', channel: contact.channel, to: contact.to, expiresIn: 600 };
}

function verify(body) {
  const contact = normalizeContact(body);
  const code = typeof body.code === 'string' || typeof body.code === 'number' ? String(body.code).trim() : '';
  if (!/^\d{6}$/.test(code)) fail(400, 'Код должен содержать 6 цифр');

  const user = findUser(contact);
  if (!user) fail(400, 'Пользователь не найден, запросите новый код');

  const active = store
    .filter('codes', (row) => row.userId === user.id && row.to === contact.to && !row.used)
    .sort((left, right) => String(right.createdAt).localeCompare(String(left.createdAt)));
  const row = active[0];
  if (!row) fail(400, 'Код не найден или уже использован, запросите новый код');

  if (new Date(row.expiresAt).getTime() <= Date.now()) {
    store.update('codes', row.id, { used: true });
    fail(400, 'Срок действия кода истёк, запросите новый код');
  }
  if (Number(row.attempts) >= MAX_CODE_ATTEMPTS) {
    store.update('codes', row.id, { used: true });
    fail(429, 'Слишком много неверных попыток, запросите новый код');
  }
  if (sha256(code) !== row.hash) {
    store.update('codes', row.id, { attempts: (Number(row.attempts) || 0) + 1 });
    fail(400, 'Неверный код');
  }

  store.update('codes', row.id, { used: true, usedAt: nowIso() });
  const token = crypto.randomBytes(32).toString('hex');
  store.insert('sessions', {
    id: crypto.randomBytes(12).toString('hex'),
    userId: user.id,
    hash: sha256(token),
    expiresAt: new Date(Date.now() + TOKEN_TTL_MS).toISOString(),
    createdAt: nowIso()
  });

  return { token: token, user: publicUser(user) };
}

function requireAuth(req) {
  const header = typeof req.headers.authorization === 'string' ? req.headers.authorization : '';
  const match = /^Bearer\s+(\S+)$/i.exec(header);
  if (!match) fail(401, 'Требуется Bearer-токен');
  const session = store.find(
    'sessions',
    (row) => row.hash === sha256(match[1]) && new Date(row.expiresAt).getTime() > Date.now()
  );
  if (!session) fail(401, 'Недействительный или истёкший токен');
  const user = store.find('users', (row) => row.id === session.userId && !row.deletedAt);
  if (!user) fail(401, 'Пользователь не найден');
  return user;
}

function meResponse(user) {
  return { user: publicUser(user), usageToday: store.usageMap(user.id, today()) };
}

module.exports = { register, verify, requireAuth, meResponse, publicUser };
