# MEGA CREATOR AI — сервер

Рабочий Node.js-сервер **без npm-зависимостей** (только встроенные `http`, `fs`, `path`, `crypto` и глобальный `fetch`). Требуется Node.js 18+.

```bash
cd server
node server.js          # или npm start
```

Данные хранятся в `server/data/store.json` (папка создаётся автоматически, запись через temp-файл + `rename`). Папка `data/` добавлена в `.gitignore`. Секреты **никогда** не отдаются клиенту: в `GET /api/config` только булевы флаги готовности провайдеров.

## Переменные окружения

| Переменная | Назначение | Пример / по умолчанию |
| --- | --- | --- |
| `PORT` | Порт HTTP-сервера | `3000` |
| `DEV_MODE` | Тестовый режим: код возвращается в ответе, доступна активация тарифа без оплаты | `false`; включить `true` только локально |
| `CODE_WEBHOOK_URL` | URL сервиса доставки SMS/e-mail; сервер шлёт `POST {to, code, channel}` | не задан → register вернёт `501` |
| `AI_BASE_URL` | Базовый URL OpenAI-совместимого текстового API | `https://api.openai.com` |
| `AI_API_KEY` | Ключ текстового API | — |
| `AI_MODEL` | Модель чата | `gpt-4o-mini` |
| `IMAGE_BASE_URL` | Базовый URL генерации изображений | `https://api.openai.com` |
| `IMAGE_API_KEY` | Ключ API изображений | — |
| `IMAGE_MODEL` | Модель генерации изображений | например `gpt-image-1` |
| `VIDEO_BASE_URL` | Базовый URL видео-провайдера | — |
| `VIDEO_API_KEY` | Ключ видео-провайдера | — |
| `VIDEO_MODEL` | Модель видео | — |
| `VIDEO_PATH` | Путь генерации видео | `/v1/videos` |
| `PAYMENT_SHOP_ID` | Идентификатор магазина ЮKassa | — |
| `PAYMENT_SECRET_KEY` | Секретный ключ ЮKassa | — |
| `RETURN_URL` | URL возврата после оплаты | `https://example.com/payment-return` |
| `TARIFF_PRICES` | Цены тарифов в RUB (JSON-объект) | `{"PRO":299,"PREMIUM":599}` |

Провайдер считается настроенным только если заданы `*_BASE_URL`, `*_API_KEY` и модель. Не настроенный провайдер даёт `503`, ошибка провайдера — `502`, реальные результаты не имитируются. Таймаут внешнего вызова — 30 секунд (`AbortSignal.timeout`).

Пример запуска (без записи секретов в файлы):

```bash
DEV_MODE=true PORT=3000 AI_BASE_URL=https://api.openai.com AI_API_KEY=... node server.js
```

## Лимиты

Счётчик хранится по паре `пользователь + инструмент + дата (UTC)`. Списание происходит **только после успешного вызова провайдера**.

| Инструмент | FREE | PRO | PREMIUM |
| --- | ---: | ---: | ---: |
| idei (идеи) | 5 | 50 | 150 |
| script (сценарий) | 2 | 25 | 80 |
| ad, rewrite | 3 | 40 | 120 |
| hashtags, description | 5 | 50 | 150 |
| greeting | 5 | 50 | 150 |
| plan | 1 | 30 | 100 |
| templates | 3 | 30 | 100 |
| voice | 2 | 30 | 100 |
| photo | 0 | 0 | 20 |
| video | 0 | 0 | 8 |

`photo` и `video` доступны только на `PREMIUM` (иначе `403`). При исчерпании дневного лимита — `429`.

## Эндпоинты

- `GET /` — приветствие; `GET /health` — проверка жизни; `OPTIONS *` — `204`.
- `GET /api/config` — флаги `devMode`, `codeDelivery`, `ai`, `images`, `videos`, `payments`.
- `POST /api/auth/register` — `{phone?, email?, name?}`, хотя бы один контакт. Код живёт 10 минут и хранится только хэшем SHA-256.
- `POST /api/auth/verify` — `{phone|email, code}`, выдаёт Bearer-токен на 30 дней.
- `GET /api/me` — пользователь + `usageToday`.
- `POST /api/generate` — `{tool, prompt, audience?, benefit?}` → `{result, remaining}`.
- `POST /api/images/generate` — `{prompt}` → `{result, remaining}` (URL или `data:`-URL).
- `POST /api/videos/generate` — `{prompt}` → `{result, remaining}`.
- `POST /api/payments/create` — `{tariff}` → `{confirmationUrl, paymentId}`.
- `POST /api/payments/webhook` — уведомление ЮKassa; платёж перепроверяется через API.
- `POST /api/payments/dev-activate` — только при `DEV_MODE=true`, `{tariff}`; помечен как тестовый.
- `GET/POST /api/projects`, `DELETE /api/projects/:id`, `DELETE /api/account`.

## Примеры запросов (curl)

Базовый адрес: `BASE=http://localhost:3000`.

```bash
# Проверка сервера и доступных провайдеров
curl "$BASE/health"
curl "$BASE/api/config"

# Регистрация: в DEV_MODE код просто возвращается в ответе
curl -s -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"phone":"+7 999 123-45-67","name":"Анна"}'
# -> {"devCode":"123456", ...}

# Подтверждение кода и получение токена
curl -s -X POST "$BASE/api/auth/verify" \
  -H 'Content-Type: application/json' \
  -d '{"phone":"79991234567","code":"123456"}'
# -> {"token":"...","user":{...}}

TOKEN="вставьте token из ответа"

# Профиль и использованные сегодня лимиты
curl -H "Authorization: Bearer $TOKEN" "$BASE/api/me"

# Генерация текста (промпт собирается на сервере)
curl -s -X POST "$BASE/api/generate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"tool":"script","prompt":"Продвижение кофейни","audience":"студенты","benefit":"скидка утром"}'
# -> {"result":"...","remaining":1}

# Изображение и видео (только PREMIUM)
curl -s -X POST "$BASE/api/images/generate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"prompt":"чашка кофе на столе"}'

curl -s -X POST "$BASE/api/videos/generate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"prompt":"пять секунд пара над чашкой кофе"}'

# Создание платежа в ЮKassa
curl -s -X POST "$BASE/api/payments/create" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"tariff":"PRO"}'
# -> {"confirmationUrl":"https://yoomoney.ru/checkout/...","paymentId":"..."}

# Тестовая активация тарифа (только DEV_MODE=true, реальных денег нет)
curl -s -X POST "$BASE/api/payments/dev-activate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"tariff":"PREMIUM"}'

# Проекты
curl -H "Authorization: Bearer $TOKEN" "$BASE/api/projects"
curl -s -X POST "$BASE/api/projects" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Ролик про кофе","category":"video","content":"Сценарий...","favorite":false}'
curl -X DELETE -H "Authorization: Bearer $TOKEN" "$BASE/api/projects/ID_PROEKTA"

# Удалить аккаунт вместе с данными
curl -X DELETE -H "Authorization: Bearer $TOKEN" "$BASE/api/account"
```

Формат webhook ЮKassa (сервер не верит `'status': 'succeeded'` из тела, а делает `GET /v3/payments/{id}` с Basic-авторизацией):

```json
{
  "type": "notification",
  "event": "payment.succeeded",
  "object": { "id": "2e3c4a5b-..." }
}
```

## Ограничения

- Хранилище JSON подходит для одного процесса; при нескольких инстансах нужна общая БД (см. `schema.sql` как справочник).
- При повреждении `store.json` сервер делает резервную копию `store.json.broken-<timestamp>` и создаёт пустое хранилище.
- Видеопровайдеры с асинхронной генерацией могут вернуть только идентификатор задачи: в этом случае сервер ответит `502` (результат не выдумывается); поддержка polling не реализована.
- `DEV_MODE` и `dev-activate` запрещены в продакшене.
