'use strict';

const store = require('./store');
const { fail, randomId, nowIso } = require('./util');

/**
 * Режим «Всё бесплатно»: все инструменты открыты на любом тарифе,
 * лимиты практические неограничены (100 000 в день), оплата не требуется.
 */
const DAILY = 100000;

function allFeatures() {
  return {
    idei: DAILY, script: DAILY, ad: DAILY, rewrite: DAILY,
    hashtags: DAILY, description: DAILY, greeting: DAILY, plan: DAILY,
    templates: DAILY, voice: DAILY, photo: DAILY, video: DAILY
  };
}

const LIMITS = {
  FREE: allFeatures(),
  PRO: allFeatures(),
  PREMIUM: allFeatures()
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
    fail(400, 'Неизвестный инструмент. Доступны: idei, script, ad, rewrite, hashtags, description, greeting, plan, templates, voice, photo, video');
  }
  return feature;
}

/** Проверка до вызова провайдера: клиент не может обойти общий лимит. */
function check(user, feature) {
  const tariff = LIMITS[user.tariff] ? user.tariff : 'FREE';
  const limit = LIMITS[tariff][feature];
  const day = new Date().toISOString().slice(0, 10);
  const used = store.usageCount(user.id, feature, day);
  const remaining = Math.max(0, limit - used);

  if (remaining === 0) {
    fail(429, `Дневной лимит «${feature}» исчерпан (${limit} в день). Попробуйте завтра`, {
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
