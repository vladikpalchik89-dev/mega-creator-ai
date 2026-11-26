'use strict';

const { HttpError } = require('./util');

function json(res, status, body, extraHeaders) {
  const headers = Object.assign(
    { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' },
    extraHeaders || {}
  );
  res.writeHead(status, headers);
  res.end(JSON.stringify(body));
}

function errorResponse(res, error) {
  if (res.headersSent || res.writableEnded) {
    res.end();
    return;
  }
  if (error instanceof HttpError) {
    json(res, error.status, Object.assign({ error: error.message }, error.details || {}));
    return;
  }
  console.error('[server] unexpected error:', error && error.stack ? error.stack : error);
  json(res, 500, { error: 'Внутренняя ошибка сервера' });
}

function readJson(req, limitBytes) {
  const maxBytes = limitBytes || 1024 * 1024;
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    let settled = false;

    req.on('data', (chunk) => {
      if (settled) return;
      size += chunk.length;
      if (size > maxBytes) {
        settled = true;
        reject(new HttpError(413, 'Слишком большой запрос'));
        return;
      }
      chunks.push(chunk);
    });

    req.on('end', () => {
      if (settled) return;
      settled = true;
      const raw = Buffer.concat(chunks).toString('utf8').trim();
      if (!raw) {
        resolve({});
        return;
      }
      try {
        const parsed = JSON.parse(raw);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          throw new Error('JSON body must be an object');
        }
        resolve(parsed);
      } catch (error) {
        reject(new HttpError(400, 'Некорректный JSON в теле запроса'));
      }
    });

    req.on('error', (error) => {
      if (settled) return;
      settled = true;
      reject(error);
    });
  });
}

module.exports = { json, errorResponse, readJson };
