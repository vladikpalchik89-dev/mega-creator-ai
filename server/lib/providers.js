'use strict';

const { config, configured } = require('./config');
const { fail, clip } = require('./util');

const TIMEOUT_MS = 30000;

// Бесплатные публичные эндпоинты без ключей: работают как fallback,
// если пользовательские AI_* / IMAGE_* не заданы.
const FREE_TEXT_URL = 'https://text.pollinations.ai/';
const FREE_IMAGE_URL = 'https://image.pollinations.ai/prompt/';

function textConfigured() {
  return true;
}

function imageConfigured() {
  return true;
}

async function requestText(url) {
  let response;
  try {
    response = await fetch(url, { signal: AbortSignal.timeout(TIMEOUT_MS) });
  } catch (error) {
    fail(502, 'Бесплатный ИИ-провайдер недоступен или превысил время ожидания');
  }
  if (!response.ok) {
    fail(502, `Бесплатный ИИ-провайдер вернул HTTP ${response.status}`);
  }
  let text = '';
  try {
    text = await response.text();
  } catch (error) {
    text = '';
  }
  if (!String(text).trim()) fail(502, 'Бесплатный ИИ-провайдер вернул пустой ответ');
  return String(text).trim();
}

function videoConfigured() {
  return configured(config.video);
}

function joinUrl(base, endpoint) {
  return String(base).replace(/\/+$/, '') + '/' + String(endpoint).replace(/^\/+/, '');
}

async function requestJson(url, options) {
  let response;
  try {
    response = await fetch(url, Object.assign({}, options, { signal: AbortSignal.timeout(TIMEOUT_MS) }));
  } catch (error) {
    fail(502, 'Провайдер недоступен или превышено время ожидания');
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
    const detail = data && data.error && data.error.message
      ? String(data.error.message)
      : (data && data.message ? String(data.message) : '');
    fail(502, detail ? `Ошибка провайдера: ${clip(detail, 120)}` : `Ошибка провайдера (HTTP ${response.status})`);
  }
  if (!data || typeof data !== 'object') fail(502, 'Провайдер вернул некорректный ответ');
  return data;
}

const RULES = {
  idei: 'Предложи 10 конкретных идей для контента с коротким пояснением, почему каждая идея может сработать.',
  script: 'Напиши короткий сценарий ролика: крючок (зацепка), основная часть и призыв к действию.',
  ad: 'Напиши 3 варианта короткого рекламного объявления с акцентом на пользу для аудитории.',
  rewrite: 'Перепиши исходный текст, сохранив смысл, но сделав его яснее, короче и убедительнее.',
  hashtags: 'Подбери 20 релевантных хештегов на русском языке, от общих к более точным.',
  description: 'Напиши краткое продающее описание: что это, кому подходит и какую пользу даёт.',
  greeting: 'Напиши дружелюбное приветствие для первой коммуникации с клиентом или подписчиком.',
  plan: 'Составь пошаговый план с конкретными действиями и короткими сроками.'
};

function buildPrompt(feature, body) {
  const prompt = clip(body.prompt, 6000);
  const audience = clip(body.audience, 300);
  const benefit = clip(body.benefit, 300);
  const context = [
    prompt ? `Исходная идея или текст: ${prompt}` : '',
    audience ? `Целевая аудитория: ${audience}` : '',
    benefit ? `Ключевая польза: ${benefit}` : ''
  ].filter(Boolean).join('\n');

  const system = 'Ты опытный русскоязычный маркетолог и копирайтер. Отвечай только по-русски, ' +
    'практично и без воды. Не выдумывай фактов и обещаний, которых нет в исходных данных. ' +
    (RULES[feature] || RULES.idei);
  const user = `Инструмент: ${feature}.\n${context}\nВерни готовый результат без пояснений и обёрток.`;
  return { system: system, user: user };
}

async function generateText(feature, body) {
  const messages = buildPrompt(feature, body);

  // Бесплатный режим без ключей: публичный текстовый ИИ.
  if (!configured(config.ai)) {
    return requestText(FREE_TEXT_URL + encodeURIComponent(
      `${messages.system}\n\n${messages.user}`
    ));
  }

  const data = await requestJson(joinUrl(config.ai.baseUrl, 'chat/completions'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${config.ai.apiKey}`
    },
    body: JSON.stringify({
      model: config.ai.model,
      messages: [
        { role: 'system', content: messages.system },
        { role: 'user', content: messages.user }
      ],
      temperature: 0.7
    })
  });

  const choice = Array.isArray(data.choices) ? data.choices[0] : null;
  const content = choice && choice.message && typeof choice.message.content === 'string'
    ? choice.message.content
    : (choice && typeof choice.text === 'string' ? choice.text : '');
  if (!String(content).trim()) fail(502, 'Провайдер вернул пустой текст');
  return String(content).trim();
}

async function generateImage(prompt) {
  // Бесплатный режим без ключей: публичная генерация изображений.
  if (!configured(config.image)) {
    const url = FREE_IMAGE_URL + encodeURIComponent(prompt)
      + '?width=1024&height=1024&nologo=true';
    let response;
    try {
      response = await fetch(url, { signal: AbortSignal.timeout(TIMEOUT_MS) });
    } catch (error) {
      fail(502, 'Бесплатный сервис изображений недоступен');
    }
    if (!response.ok) fail(502, `Сервис изображений вернул HTTP ${response.status}`);
    const type = String(response.headers.get('content-type') || '');
    if (!type.startsWith('image/')) fail(502, 'Сервис изображений вернул не изображение');
    return response.url || url;
  }

  const data = await requestJson(joinUrl(config.image.baseUrl, 'v1/images/generations'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${config.image.apiKey}`
    },
    body: JSON.stringify({ model: config.image.model, prompt: prompt, n: 1, size: '1024x1024' })
  });

  const item = Array.isArray(data.data) ? data.data[0] : null;
  if (item && typeof item.url === 'string' && item.url) return item.url;
  if (item && typeof item.image_url === 'string' && item.image_url) return item.image_url;
  if (item && typeof item.b64_json === 'string' && item.b64_json) {
    return `data:image/png;base64,${item.b64_json}`;
  }
  fail(502, 'Провайдер не вернул изображение');
}

function extractVideoUrl(data) {
  const candidates = [
    data.video_url,
    data.url,
    data.output,
    data.video,
    data.result && data.result.video_url,
    data.result && data.result.url,
    data.data && !Array.isArray(data.data) && data.data.url,
    data.data && !Array.isArray(data.data) && data.data.video_url,
    data.data && !Array.isArray(data.data) && data.data.output,
    Array.isArray(data.data) && data.data[0] && data.data[0].url,
    Array.isArray(data.data) && data.data[0] && data.data[0].video_url,
    Array.isArray(data.data) && data.data[0] && data.data[0].output,
    Array.isArray(data.videos) && data.videos[0] && data.videos[0].url
  ];
  return candidates.find((value) => typeof value === 'string' && /^(https?:\/\/|data:)/.test(value)) || null;
}

async function generateVideo(prompt) {
  if (!videoConfigured()) {
    fail(503, 'Генерация видео не настроена: укажите VIDEO_BASE_URL, VIDEO_API_KEY, VIDEO_MODEL и VIDEO_PATH');
  }
  const data = await requestJson(joinUrl(config.video.baseUrl, config.video.path), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${config.video.apiKey}`
    },
    body: JSON.stringify({ model: config.video.model, prompt: prompt })
  });

  const url = extractVideoUrl(data);
  if (!url) fail(502, 'Провайдер не вернул ссылку на видео');
  return url;
}

module.exports = {
  textConfigured,
  imageConfigured,
  videoConfigured,
  generateText,
  generateImage,
  generateVideo,
  extractVideoUrl
};
