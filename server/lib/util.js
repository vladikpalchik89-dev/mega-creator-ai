'use strict';

const crypto = require('crypto');

class HttpError extends Error {
  constructor(status, message, details) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.details = details || {};
  }
}

function fail(status, message, details) {
  throw new HttpError(status, message, details);
}

function sha256(value) {
  return crypto.createHash('sha256').update(String(value)).digest('hex');
}

function randomId(bytes) {
  return crypto.randomBytes(bytes || 12).toString('hex');
}

function nowIso() {
  return new Date().toISOString();
}

function today() {
  return nowIso().slice(0, 10);
}

function clip(value, max) {
  const source = value === undefined || value === null ? '' : String(value);
  const result = source.trim();
  return result.length > max ? result.slice(0, max) : result;
}

function requiredString(value, name, max) {
  const result = typeof value === 'string' ? value.trim() : '';
  if (!result) fail(400, `Укажите поле ${name}`);
  if (result.length > (max || 4000)) fail(400, `Превышена максимальная длина поля ${name}`);
  return result;
}

module.exports = { HttpError, fail, sha256, randomId, nowIso, today, clip, requiredString };
