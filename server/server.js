'use strict';

/**
 * MEGA CREATOR AI — HTTP-сервер без внешних npm-зависимостей.
 * Все секреты и ключи читаются только из переменных окружения.
 * Данные хранятся в server/data/store.json (создаётся при запуске).
 */
const http = require('http');

const { config, publicStatus } = require('./lib/config');
const { json, errorResponse, readJson } = require('./lib/http');
const auth = require('./lib/auth');
const limits = require('./lib/limits');
const providers = require('./lib/providers');
const payments = require('./lib/payments');
const store = require('./lib/store');
const { fail, clip, requiredString, randomId, nowIso } = require('./lib/util');

const TEXT_TOOLS = new Set([
  'idei', 'script', 'ad', 'rewrite', 'hashtags', 'description', 'greeting', 'plan'
]);

function projectIdFrom(pathname) {
  const match = /^\/api\/projects\/([^/]+)$/.exec(pathname);
  if (!match) return null;
  try {
    return decodeURIComponent(match[1]);
  } catch (error) {
    fail(400, 'Некорректный идентификатор проекта');
  }
}

async function handle(req, res) {
  const url = new URL(req.url || '/', 'http://localhost');
  const pathname = url.pathname;

  if (req.method === 'OPTIONS') {
    res.writeHead(204, { Allow: 'GET, POST, DELETE, OPTIONS' });
    res.end();
    return;
  }

  if (req.method === 'GET' && pathname === '/') {
    json(res, 200, { service: 'MEGA CREATOR AI', status: 'ok', docs: 'server/README.md' });
    return;
  }

  if (req.method === 'GET' && pathname === '/health') {
    json(res, 200, { status: 'ok', service: 'MEGA CREATOR AI', time: nowIso() });
    return;
  }

  if (req.method === 'GET' && pathname === '/api/config') {
    json(res, 200, publicStatus());
    return;
  }

  if (req.method === 'POST' && pathname === '/api/auth/register') {
    json(res, 200, await auth.register(await readJson(req)));
    return;
  }

  if (req.method === 'POST' && pathname === '/api/auth/verify') {
    json(res, 200, auth.verify(await readJson(req)));
    return;
  }

  if (req.method === 'GET' && pathname === '/api/me') {
    json(res, 200, auth.meResponse(auth.requireAuth(req)));
    return;
  }

  if (req.method === 'POST' && pathname === '/api/generate') {
    const user = auth.requireAuth(req);
    const body = await readJson(req);
    const feature = limits.featureOf(body.tool);
    if (!TEXT_TOOLS.has(feature)) fail(400, 'Этот инструмент недоступен в /api/generate');
    const prompt = requiredString(body.prompt, 'prompt', 6000);
    if (!providers.textConfigured()) {
      fail(503, 'Текстовый ИИ-провайдер не настроен: укажите AI_BASE_URL, AI_API_KEY и AI_MODEL');
    }
    const state = limits.check(user, feature);
    const result = await providers.generateText(feature, Object.assign({}, body, { prompt: prompt }));
    const consumed = limits.consume(user, state, 'chat-completions');
    json(res, 200, { result: result, remaining: consumed.remaining });
    return;
  }

  if (req.method === 'POST' && pathname === '/api/images/generate') {
    const user = auth.requireAuth(req);
    const body = await readJson(req);
    const prompt = requiredString(body.prompt, 'prompt', 4000);
    if (!providers.imageConfigured()) {
      fail(503, 'Генерация изображений не настроена: укажите IMAGE_BASE_URL, IMAGE_API_KEY и IMAGE_MODEL');
    }
    const state = limits.check(user, 'photo');
    const result = await providers.generateImage(prompt);
    const consumed = limits.consume(user, state, 'images-generations');
    json(res, 200, { result: result, remaining: consumed.remaining });
    return;
  }

  if (req.method === 'POST' && pathname === '/api/videos/generate') {
    const user = auth.requireAuth(req);
    const body = await readJson(req);
    const prompt = requiredString(body.prompt, 'prompt', 4000);
    if (!providers.videoConfigured()) {
      fail(503, 'Генерация видео не настроена: укажите VIDEO_BASE_URL, VIDEO_API_KEY, VIDEO_MODEL и VIDEO_PATH');
    }
    const state = limits.check(user, 'video');
    const result = await providers.generateVideo(prompt);
    const consumed = limits.consume(user, state, 'videos');
    json(res, 200, { result: result, remaining: consumed.remaining });
    return;
  }

  if (req.method === 'POST' && pathname === '/api/payments/create') {
    const user = auth.requireAuth(req);
    const body = await readJson(req);
    json(res, 200, await payments.createPayment(user, body.tariff));
    return;
  }

  if (req.method === 'POST' && pathname === '/api/payments/webhook') {
    json(res, 200, await payments.applyWebhook(await readJson(req)));
    return;
  }

  if (req.method === 'POST' && pathname === '/api/payments/dev-activate') {
    if (!config.devMode) fail(404, 'Not found');
    const user = auth.requireAuth(req);
    const body = await readJson(req);
    json(res, 200, payments.devActivate(user, body.tariff));
    return;
  }

  if (req.method === 'GET' && pathname === '/api/projects') {
    const user = auth.requireAuth(req);
    const projects = store
      .filter('projects', (project) => project.userId === user.id)
      .sort((left, right) => String(right.updatedAt || '').localeCompare(String(left.updatedAt || '')));
    json(res, 200, { projects: projects });
    return;
  }

  if (req.method === 'POST' && pathname === '/api/projects') {
    const user = auth.requireAuth(req);
    const body = await readJson(req);
    const now = nowIso();
    const project = store.insert('projects', {
      id: randomId(),
      userId: user.id,
      title: requiredString(body.title, 'title', 255),
      category: body.category === undefined || body.category === null || body.category === ''
        ? 'general'
        : requiredString(body.category, 'category', 48),
      content: requiredString(body.content, 'content', 200000),
      fileUrl: body.fileUrl === undefined || body.fileUrl === null ? null : clip(body.fileUrl, 1000),
      favorite: Boolean(body.favorite),
      createdAt: now,
      updatedAt: now
    });
    json(res, 201, { project: project });
    return;
  }

  const projectId = projectIdFrom(pathname);
  if (req.method === 'DELETE' && projectId) {
    const user = auth.requireAuth(req);
    const project = store.find('projects', (row) => row.id === projectId && row.userId === user.id);
    if (!project) fail(404, 'Проект не найден');
    store.remove('projects', (row) => row.id === projectId && row.userId === user.id);
    json(res, 200, { ok: true });
    return;
  }

  if (req.method === 'DELETE' && pathname === '/api/account') {
    const user = auth.requireAuth(req);
    for (const collection of ['projects', 'generations', 'payments', 'usage', 'codes', 'sessions']) {
      store.remove(collection, (row) => row.userId === user.id);
    }
    store.remove('users', (row) => row.id === user.id);
    json(res, 200, { ok: true, message: 'Аккаунт и все данные удалены' });
    return;
  }

  json(res, 404, { error: 'Not found' });
}

const server = http.createServer((req, res) => {
  handle(req, res).catch((error) => errorResponse(res, error));
});

server.listen(config.port, () => {
  console.log(`MEGA CREATOR AI server on http://localhost:${config.port}`);
});
