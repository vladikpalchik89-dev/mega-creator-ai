'use strict';

const path = require('path');

function text(name, fallback = '') {
  const value = process.env[name];
  return value === undefined || value === null ? fallback : String(value).trim();
}

function flag(name) {
  return ['1', 'true', 'yes', 'on'].includes(text(name).toLowerCase());
}

function objectFromEnv(name) {
  const raw = text(name);
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed;
  } catch (error) {
    console.warn(`[config] ${name} is not a valid JSON object: ${error.message}`);
  }
  return {};
}

const config = {
  port: Number(text('PORT', '3000')) || 3000,
  devMode: flag('DEV_MODE'),
  dataDir: path.resolve(__dirname, '..', 'data'),
  codeWebhookUrl: text('CODE_WEBHOOK_URL'),
  ai: {
    baseUrl: text('AI_BASE_URL'),
    apiKey: text('AI_API_KEY'),
    model: text('AI_MODEL', 'gpt-4o-mini')
  },
  image: {
    baseUrl: text('IMAGE_BASE_URL'),
    apiKey: text('IMAGE_API_KEY'),
    model: text('IMAGE_MODEL')
  },
  video: {
    baseUrl: text('VIDEO_BASE_URL'),
    apiKey: text('VIDEO_API_KEY'),
    model: text('VIDEO_MODEL'),
    path: text('VIDEO_PATH', '/v1/videos')
  },
  payments: {
    shopId: text('PAYMENT_SHOP_ID'),
    secretKey: text('PAYMENT_SECRET_KEY'),
    returnUrl: text('RETURN_URL'),
    prices: objectFromEnv('TARIFF_PRICES')
  }
};

/** Провайдер считается настроенным только при наличии адреса, ключа и модели. */
function configured(provider) {
  return Boolean(provider && provider.baseUrl && provider.apiKey && provider.model);
}

function paymentsConfigured() {
  return Boolean(config.payments.shopId && config.payments.secretKey);
}

/** Только булевы флаги: никаких ключей наружу не отдаём. */
function publicStatus() {
  return {
    devMode: config.devMode,
    codeDelivery: Boolean(config.codeWebhookUrl),
    freeAi: true,
    ai: true,
    images: true,
    videos: configured(config.video),
    payments: paymentsConfigured() && Boolean(config.payments.returnUrl)
  };
}

module.exports = { config, configured, paymentsConfigured, publicStatus };
