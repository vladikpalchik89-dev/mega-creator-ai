# MEGA CREATOR AI — сервер

Рабочий Node.js-сервер **без npm-зависимостей** (только встроенные `http`, `fs`, `path`, `crypto` и глобальный `fetch`). Требуется Node.js 18+.

```bash
cd server
node server.js          # или npm start
```

Данные хранятся в `server/data/store.json` (папка создаётся автоматически, запись через temp-файл + `rename`). Папка `data/` добавлена в `.gitignore`. Секреты **никогда** не отдаются клиенту: в `GET /api/config` только булевы флаги готовности провайдеров.

**Режим «Всё бесплатно» включён по умолчанию:** все инструменты открыты на любом тарифе, дневной лимит 100 000, оплата не нужна. Текст и изображения генерируются бесплатными публичными эндпоинтами Pollinations без ключей. Видео требует `VIDEO_*` (у Pollinations нужен бесплатный аккаунтный ключ).

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

Если `AI_*` или `IMAGE_*` не заданы, сервер автоматически использует бесплатные публичные API (`text.pollinations.ai`, `image.pollinations.ai`) — ключи не нужны. Если заданы, используется ваш OpenAI-совместимый провайдер. Видео работает только при заданных `VIDEO_*`. Ошибка провайдера даёт `502`, таймаут внешнего вызова — 30 секунд.

Пример запуска (без записи секретов в файлы):

```bash
DEV_MODE=true PORT=3000 node server.js
# бесплатный ИИ без ключей уже работает; при желании добавьте AI_* / IMAGE_* / VIDEO_*
```

## Лимиты

Счётчик хранится по паре `пользователь + инструмент + дата (UTC)`. Списание происходит **только после успешного вызова провайдера**.

Все инструменты на всех тарифах: **100 000 в день**.

| Инструмент | FREE | PRO | PREMIUM |
| --- | ---: | ---: | ---: |
| idei, script, ad, rewrite, hashtags, description, greeting, plan | 100000 | 100000 | 100000 |
| templates, voice, photo, video | 100000 | 100000 | 100000 |

Оплата и тарифы не влияют на доступ. `403` больше не используется для ограничения инструментов; при исчерпании 100 000 возвращается `429`.

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
