'use strict';

const fs = require('fs');
const path = require('path');
const { config } = require('./config');
const { randomId, today } = require('./util');

const COLLECTIONS = ['users', 'sessions', 'codes', 'projects', 'generations', 'payments', 'usage'];
const storeFile = path.join(config.dataDir, 'store.json');

function emptyStore() {
  const data = { version: 1 };
  for (const name of COLLECTIONS) data[name] = [];
  return data;
}

function writeStore(data) {
  fs.mkdirSync(config.dataDir, { recursive: true });
  const temporary = `${storeFile}.tmp`;
  fs.writeFileSync(temporary, JSON.stringify(data, null, 2), 'utf8');
  fs.renameSync(temporary, storeFile);
}

function loadStore() {
  fs.mkdirSync(config.dataDir, { recursive: true });
  if (!fs.existsSync(storeFile)) {
    const data = emptyStore();
    writeStore(data);
    return data;
  }
  try {
    const parsed = JSON.parse(fs.readFileSync(storeFile, 'utf8'));
    const data = emptyStore();
    for (const name of COLLECTIONS) {
      if (Array.isArray(parsed[name])) data[name] = parsed[name];
    }
    return data;
  } catch (error) {
    const backup = `${storeFile}.broken-${Date.now()}`;
    try {
      fs.renameSync(storeFile, backup);
    } catch (renameError) {
      console.error('[store] cannot backup broken store:', renameError.message);
    }
    console.error('[store] store.json is damaged, starting empty. Backup:', backup, error.message);
    const data = emptyStore();
    writeStore(data);
    return data;
  }
}

// Синхронные записи: Node обрабатывает запросы в одном потоке, поэтому
// check-then-write внутри одного обработчика не создаёт гонок на файле.
const db = loadStore();

function save() {
  writeStore(db);
}

function insert(collection, item) {
  db[collection].push(item);
  save();
  return item;
}

function find(collection, predicate) {
  return db[collection].find(predicate) || null;
}

function filter(collection, predicate) {
  return db[collection].filter(predicate);
}

function update(collection, id, patch) {
  const item = db[collection].find((entry) => entry.id === id);
  if (!item) return null;
  Object.assign(item, patch);
  save();
  return item;
}

function remove(collection, predicate) {
  const before = db[collection].length;
  db[collection] = db[collection].filter((entry) => !predicate(entry));
  const removed = before - db[collection].length;
  if (removed > 0) save();
  return removed;
}

function usageCount(userId, feature, day) {
  const row = find('usage', (entry) => entry.userId === userId && entry.feature === feature && entry.day === day);
  return row ? Number(row.count) || 0 : 0;
}

function usageMap(userId, day) {
  const result = {};
  for (const row of db.usage) {
    if (row.userId === userId && row.day === day) result[row.feature] = Number(row.count) || 0;
  }
  return result;
}

function bumpUsage(userId, feature, day) {
  const row = find('usage', (entry) => entry.userId === userId && entry.feature === feature && entry.day === day);
  if (row) {
    row.count = (Number(row.count) || 0) + 1;
    save();
    return row.count;
  }
  insert('usage', {
    id: randomId(),
    userId: userId,
    feature: feature,
    day: day || today(),
    count: 1,
    createdAt: new Date().toISOString()
  });
  return 1;
}

module.exports = {
  db,
  storeFile,
  save,
  insert,
  find,
  filter,
  update,
  remove,
  usageCount,
  usageMap,
  bumpUsage
};
