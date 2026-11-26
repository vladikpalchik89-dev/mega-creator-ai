'use strict';

const store = require('./store');
const { fail, randomId, nowIso } = require('./util');

/**
 * Дневные лимиты сервера. Значения совпадают с лимитами приложения:
 * FREE 5/2/3/5/1/3/2/0/0, PRO 50/25/40/50/30/30/30/0/0,
 * PREMIUM 150/80/120/150/100/100/100/20/8.
 */
const LIMITS = {
  FREE: {
    idei: 5, script: 2, ad: 3, rewrite: 3, hashtags: 5, description: 5,
    greeting: 5, plan: 1, templates: 3, voice: 2, photo: 0, video: 0
  },
  PRO: {
    idei: 50, script: 25, ad: 40, rewrite: 40, hashtags: 50, description: 50,
    greeting: 50, plan: 30, templates: 30, voice: 30, photo: 0, video: 0
  },
  PREMIUM: {
    idei: 150, script: 80, ad: 120, rewrite: 120, hashtags: 150, description: 150,
    greeting: 150, plan: 100, templates: 100, voice: 100, photo: 20, video: 8
  }
};

const ALIASES = {
  ideas: 'idei',
  'идеи': 'idei',
  idea: 'idei',
  script: 'script',
  'сценарий': 'script',
  ad: 'ad',
  ads: 'ad',
  'реклама': 'ad',
  rewrite: 'rewrite',
  'переписывание': 'rewrite',
  hashtags: 'hashtags',
  hashtag: 'hashtags',
  'хештеги': 'hashtags',
  description: 'description',
  'описание': 'description',
  greeting: 'greeting',
  'приветствие': 'greeting',
  plan: 'plan',
  'план': 'plan',
  templates: 'templates',
  template: 'templates',
  voice: 'voice',
  photo: 'photo',
  image: 'photo',
  'фото': 'photo',
  video: 'video',
  'видео': 'video'
};

function featureOf(tool) {
  const key = String(tool === undefined || tool === null ? '' : tool).trim().toLowerCase();
  const feature = Object.prototype.hasOwnProperty.call(ALIASES, key) ? ALIASES[key] : key;
  if (!Object.prototype.hasOwnProperty.call(LIMITS.FREE, feature)) {
    fail(400, 'Неизвестный инструмент. Доступны: idei, script, ad, rewrite, hashtags, description, greeting, plan');
  }
  return feature;
}

/** Проверка до вызова провайдера: curl-клиент не может обойти тариф. */
function check(user, feature) {
  const tariff = LIMITS[user.tariff] ? user.tariff : 'FREE';
  const limit = LIMITS[tariff][feature];
  const day = new Date().toISOString().slice(0, 10);
  const used = store.usageCount(user.id, feature, day);
  const remaining = Math.max(0, limit - used);

  if (remaining === 0) {
    if (limit === 0) {
      const premiumOnly = feature === 'photo' || feature === 'video';
      fail(403, premiumOnly
        ? 'Генерация фото и видео доступна только на тарифе PREMIUM'
        : `Инструмент «${feature}» недоступен на тарифе ${tariff}`);
    }
    fail(429, `Дневной лимит «${feature}» исчерпан (${limit} в день). Попробуйте завтра или смените тариф`, {
      feature: feature,
      limit: limit,
      remaining: 0
    });
  }

  return { feature: feature, limit: limit, used: used, remaining: remaining, day: day };
}

/** Списание только после успешного ответа провайдера. */
function consume(user, state, provider) {
  const fresh = check(user, state.feature);
  const used = store.bumpUsage(user.id, fresh.feature, fresh.day);
  store.insert('generations', {
    id: randomId(),
    userId: user.id,
    feature: fresh.feature,
    provider: provider || null,
    costUnits: 1,
    createdAt: nowIso()
  });
  return { remaining: Math.max(0, fresh.limit - used) };
}

module.exports = { LIMITS, featureOf, check, consume };
