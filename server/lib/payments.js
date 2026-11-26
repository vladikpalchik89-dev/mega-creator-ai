'use strict';

const crypto = require('crypto');
const { config, paymentsConfigured } = require('./config');
const store = require('./store');
const { fail, clip, randomId, nowIso } = require('./util');

const API_URL = 'https://api.yookassa.ru/v3/payments';
const TIMEOUT_MS = 30000;

function basicAuth() {
  return 'Basic ' + Buffer.from(`${config.payments.shopId}:${config.payments.secretKey}`).toString('base64');
}

function priceOf(tariff) {
  const entry = Object.keys(config.payments.prices)
    .find((key) => key.toUpperCase() === tariff);
  const raw = entry ? config.payments.prices[entry] : undefined;
  const value = Number(raw);
  if (!Number.isFinite(value) || value <= 0) {
    fail(503, 'Цена тарифа не настроена: укажите TARIFF_PRICES, например {"PRO":299,"PREMIUM":599}');
  }
  return value.toFixed(2);
}

function normalizeTariff(value, strict) {
  const tariff = String(value === undefined || value === null ? '' : value).trim().toUpperCase();
  if (tariff === 'PRO' || tariff === 'PREMIUM') return tariff;
  if (strict !== false) fail(400, 'Некорректный тариф: используйте PRO или PREMIUM');
  return '';
}

async function yookassa(url, options) {
  let response;
  try {
    response = await fetch(url, Object.assign({}, options, {
      headers: Object.assign(
        { Authorization: basicAuth(), 'Content-Type': 'application/json' },
        options.headers || {}
      ),
      signal: AbortSignal.timeout(TIMEOUT_MS)
    }));
  } catch (error) {
    fail(502, 'ЮKassa недоступна или превышено время ожидания');
  }

  let raw = '';
  try {
    raw = await response.text();
  } catch (error) {
    raw = '';
  }
  let data = null;
  try {
    data = raw ? JSON.parse(raw) : null;
  } catch (error) {
    data = null;
  }

  if (!response.ok) {
    const detail = data && data.description ? String(data.description) : '';
    fail(502, detail ? `Ошибка ЮKassa: ${clip(detail, 120)}` : `Ошибка ЮKassa (HTTP ${response.status})`);
  }
  if (!data || typeof data !== 'object') fail(502, 'ЮKassa вернула некорректный ответ');
  return data;
}

async function createPayment(user, tariff) {
  if (!paymentsConfigured() || !config.payments.returnUrl) {
    fail(503, 'Платежи не настроены: укажите PAYMENT_SHOP_ID, PAYMENT_SECRET_KEY и RETURN_URL');
  }
  const selected = normalizeTariff(tariff);
  const amount = priceOf(selected);

  const remote = await yookassa(API_URL, {
    method: 'POST',
    headers: { 'Idempotence-Key': crypto.randomUUID() },
    body: JSON.stringify({
      amount: { value: amount, currency: 'RUB' },
      capture: true,
      confirmation: { type: 'redirect', returnUrl: config.payments.returnUrl },
      metadata: { userId: user.id, tariff: selected },
      description: `Подписка ${selected} — MEGA CREATOR AI`
    })
  });

  const confirmation = remote.confirmation || {};
  const confirmationUrl = confirmation.confirmationUrl || confirmation.url;
  if (!remote.id || typeof confirmationUrl !== 'string' || !confirmationUrl) {
    fail(502, 'ЮKassa не вернула ссылку на оплату');
  }

  store.insert('payments', {
    id: randomId(),
    paymentId: String(remote.id),
    userId: user.id,
    provider: 'yookassa',
    tariff: selected,
    amount: amount,
    currency: 'RUB',
    status: remote.status || 'pending',
    paid: false,
    applied: false,
    createdAt: nowIso()
  });

  return { confirmationUrl: confirmationUrl, paymentId: String(remote.id) };
}

/**
 * Webhook не доверяет данным клиента: платёж перепроверяется через API ЮKassa,
 * тариф меняется только при status=succeeded и paid=true.
 */
async function applyWebhook(body) {
  if (!paymentsConfigured()) {
    fail(503, 'Платежи не настроены: укажите PAYMENT_SHOP_ID и PAYMENT_SECRET_KEY');
  }

  const event = typeof body.event === 'string' ? body.event : '';
  if (event && event !== 'payment.succeeded') return { ok: true, ignored: true, event: event };

  const object = body.object && typeof body.object === 'object' ? body.object : {};
  const paymentFromBody = body.payment && typeof body.payment === 'object' ? body.payment : {};
  const paymentId = object.id || paymentFromBody.id || body.id;
  if (typeof paymentId !== 'string' || !paymentId) fail(400, 'В webhook отсутствует идентификатор платежа');

  const remote = await yookassa(`${API_URL}/${encodeURIComponent(paymentId)}`, { method: 'GET' });
  if (remote.status !== 'succeeded' || remote.paid !== true) {
    return { ok: true, applied: false, reason: 'платёж не оплачен' };
  }

  const metadata = remote.metadata && typeof remote.metadata === 'object' ? remote.metadata : {};
  const userId = String(metadata.userId === undefined || metadata.userId === null ? '' : metadata.userId);
  const tariff = normalizeTariff(metadata.tariff, false);
  if (!userId || !tariff) fail(400, 'В оплаченном платеже нет данных пользователя или тарифа');

  const user = store.find('users', (row) => row.id === userId && !row.deletedAt);
  if (!user) fail(404, 'Пользователь оплаченного платежа не найден');

  let local = store.find('payments', (row) => row.paymentId === paymentId);
  if (local && local.userId !== userId) fail(400, 'Платёж принадлежит другому пользователю');
  if (local && local.applied) {
    return { ok: true, applied: false, alreadyApplied: true, tariff: local.tariff };
  }

  if (!local) {
    local = store.insert('payments', {
      id: randomId(),
      paymentId: paymentId,
      userId: userId,
      provider: 'yookassa',
      tariff: tariff,
      amount: remote.amount && remote.amount.value ? String(remote.amount.value) : '0.00',
      currency: remote.amount && remote.amount.currency ? String(remote.amount.currency) : 'RUB',
      status: 'pending',
      paid: false,
      applied: false,
      createdAt: nowIso()
    });
  }

  store.update('payments', local.id, {
    status: 'succeeded',
    paid: true,
    verifiedAt: nowIso(),
    applied: true,
    appliedAt: nowIso(),
    tariff: tariff,
    amount: remote.amount && remote.amount.value ? String(remote.amount.value) : local.amount
  });
  store.update('users', user.id, { tariff: tariff });

  return { ok: true, applied: true, tariff: tariff };
}

/** Только для DEV_MODE=true: реальная оплата не производится. */
function devActivate(user, tariff) {
  const selected = normalizeTariff(tariff);
  store.insert('payments', {
    id: randomId(),
    paymentId: `dev-${randomId(6)}`,
    userId: user.id,
    provider: 'dev-mode',
    tariff: selected,
    amount: '0.00',
    currency: 'RUB',
    status: 'succeeded',
    paid: false,
    applied: true,
    dev: true,
    createdAt: nowIso(),
    verifiedAt: nowIso(),
    appliedAt: nowIso()
  });
  store.update('users', user.id, { tariff: selected });
  return {
    ok: true,
    dev: true,
    tariff: selected,
    warning: 'Тестовое активирование в DEV_MODE: реальная оплата не производилась'
  };
}

module.exports = { createPayment, applyWebhook, devActivate };
