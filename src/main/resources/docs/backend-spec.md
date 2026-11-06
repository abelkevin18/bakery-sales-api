# Especificación de backend: Abejita Cakes

> Contrato de API para reemplazar la persistencia mock/localStorage del panel admin.
> Se basa en lo que el código ya define (`lib/admin/types.ts`, `lib/admin/schemas.ts`, `lib/admin/repository.ts`, `lib/auth/*`), así que los nombres de campos y las validaciones coinciden con la UI actual.

---

## 0. Convenciones generales

| Aspecto | Convención |
|---|---|
| Base URL | `/api/v1` (mismo origen si se usan Route Handlers de Next.js; si no, el valor de `NEXT_PUBLIC_API_BASE_URL`) |
| Formato | JSON, `Content-Type: application/json; charset=utf-8`. Los binarios se suben directo a Supabase Storage con una URL firmada |
| Nombres | `camelCase` en la API y `snake_case` en la base de datos. El mapeo se hace en el repositorio |
| IDs | UUID v4 (`string`) |
| Fechas | ISO 8601 en UTC (`2026-10-22T15:04:05.000Z`) |
| Moneda | `priceFrom` es un número en soles (PEN) con 2 decimales. La API nunca envía el símbolo |
| Rutas públicas | `/api/v1/{recurso}`: solo lectura y solo registros activos o publicados |
| Rutas admin | `/api/v1/admin/{recurso}`: requieren sesión y devuelven todos los registros |

### Paginación, búsqueda y orden (todos los `GET` de listas en admin)

| Param | Tipo | Default | Regla |
|---|---|---|---|
| `page` | int | `1` | ≥ 1 |
| `pageSize` | int | `20` | 1–100 |
| `q` | string | — | 0–100 caracteres. Busca sin distinguir mayúsculas ni acentos (`unaccent ILIKE`) |
| `sort` | string | depende del recurso | `campo` asc, `-campo` desc. Solo se aceptan campos de una lista permitida |

**Envoltura de una lista**

```json
{
  "data": [],
  "meta": { "page": 1, "pageSize": 20, "total": 37, "totalPages": 2 }
}
```

**Envoltura de un recurso individual**

```json
{ "data": {} }
```

### Estructura de error (única para toda la API)

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Revisa los campos marcados.",
    "details": {
      "name": ["Mínimo 3 caracteres"],
      "priceFrom": ["El precio debe ser mayor a 0"]
    },
    "requestId": "req_01JBX4M8Q2"
  }
}
```

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `BAD_REQUEST` | JSON mal formado o query param inválido |
| 401 | `UNAUTHENTICATED` | No hay sesión o expiró |
| 401 | `INVALID_CREDENTIALS` | Login fallido (mensaje genérico) |
| 403 | `FORBIDDEN` | Hay sesión pero el rol no tiene permiso, o el origen/CSRF no coincide |
| 404 | `NOT_FOUND` | El ID no existe |
| 409 | `CONFLICT` | Slug duplicado, o categoría con productos asociados |
| 413 | `PAYLOAD_TOO_LARGE` | La imagen supera 5 MB |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | El archivo no es JPEG/PNG/WebP |
| 422 | `VALIDATION_ERROR` | Falla la validación del body (`details` por campo) |
| 429 | `RATE_LIMITED` | Demasiados intentos. Incluye el header `Retry-After` |
| 500 | `INTERNAL_ERROR` | Error inesperado. Nunca expone stack traces |
| 502 | `STORAGE_UNAVAILABLE` | Supabase Storage rechazó la operación o no responde |

`details` usa las mismas claves y mensajes que los esquemas Zod del frontend, así que la UI puede mostrar el error en cada campo sin transformarlo.

---

## 1. Lista de endpoints

| Recurso | Método | Ruta | Acceso |
|---|---|---|---|
| **Auth** | POST | `/api/v1/auth/login` | Público (con rate limit) |
| | POST | `/api/v1/auth/logout` | Admin |
| | GET | `/api/v1/auth/session` | Admin |
| **Productos** | GET | `/api/v1/products` | Público |
| | GET | `/api/v1/products/{id}` | Público |
| | GET | `/api/v1/admin/products` | Admin |
| | GET | `/api/v1/admin/products/{id}` | Admin |
| | POST | `/api/v1/admin/products` | Admin |
| | PATCH | `/api/v1/admin/products/{id}` | Admin |
| | DELETE | `/api/v1/admin/products/{id}` | Admin |
| **Categorías** | GET | `/api/v1/categories` | Público |
| | GET | `/api/v1/admin/categories` | Admin |
| | GET | `/api/v1/admin/categories/{id}` | Admin |
| | POST | `/api/v1/admin/categories` | Admin |
| | PATCH | `/api/v1/admin/categories/{id}` | Admin |
| | DELETE | `/api/v1/admin/categories/{id}` | Admin |
| **Testimonios** | GET | `/api/v1/testimonials` | Público |
| | GET / POST | `/api/v1/admin/testimonials` | Admin |
| | GET / PATCH / DELETE | `/api/v1/admin/testimonials/{id}` | Admin |
| **FAQs** | GET | `/api/v1/faqs` | Público |
| | GET / POST | `/api/v1/admin/faqs` | Admin |
| | GET / PATCH / DELETE | `/api/v1/admin/faqs/{id}` | Admin |
| | PUT | `/api/v1/admin/faqs/order` | Admin |
| **Uploads** | POST | `/api/v1/admin/uploads/sign` | Admin |
| | DELETE | `/api/v1/admin/uploads` | Admin |
| **Dashboard** | GET | `/api/v1/admin/stats` | Admin |

---

## 2. Detalle por endpoint

---

### 2.1 Productos

**Objeto `Product` (respuesta)**

```json
{
  "id": "3f6a2b1e-8c4d-4e9a-9b7f-1a2b3c4d5e6f",
  "name": "Torta Tres Leches",
  "categoryId": "c1a7…",
  "category": { "id": "c1a7…", "name": "Tortas clásicas", "slug": "tortas-clasicas" },
  "description": "Bizcocho húmedo bañado en tres leches con merengue italiano.",
  "size": "20 porciones",
  "priceFrom": 85.00,
  "image": "https://cdn.abejitacakes.pe/products/tres-leches-7f3a.webp",
  "featured": true,
  "active": true,
  "createdAt": "2026-09-01T14:00:00.000Z",
  "updatedAt": "2026-10-20T18:32:11.000Z"
}
```

#### `GET /api/v1/products` (público)

Devuelve solo productos con `active = true` cuya categoría también está activa. No pagina (el catálogo es chico); se cachea con el tag `products`.

| Query | Tipo | Descripción |
|---|---|---|
| `category` | slug | Filtra por el slug de la categoría |
| `featured` | `true` | Solo los destacados |

```json
{
  "data": [
    {
      "id": "…",
      "name": "Torta Tres Leches",
      "category": { "slug": "tortas-clasicas", "name": "Tortas clásicas" },
      "description": "…",
      "size": "20 porciones",
      "priceFrom": 85,
      "image": "…",
      "featured": true
    }
  ]
}
```

La vista pública **omite** `active`, `createdAt` y `updatedAt`.

#### `GET /api/v1/admin/products`

| Query | Tipo | Default | Descripción |
|---|---|---|---|
| `q` | string | — | Busca en `name` y `description` |
| `categoryId` | uuid | — | Filtro de categoría (el select de la tabla) |
| `status` | `active` \| `inactive` \| `all` | `all` | Filtro de estado |
| `featured` | `true` \| `false` | — | |
| `page`, `pageSize` | int | 1, 20 | |
| `sort` | `name`, `priceFrom`, `updatedAt`, `createdAt` | `-updatedAt` | |

Devuelve la envoltura de lista con objetos `Product` completos.

#### `POST /api/v1/admin/products`

```json
{
  "name": "Torta Selva Negra",
  "categoryId": "c1a7…",
  "description": "Bizcocho de chocolate, crema chantilly y cerezas al marrasquino.",
  "size": "15 porciones",
  "priceFrom": 95,
  "image": "https://cdn.abejitacakes.pe/products/selva-negra-91bc.webp",
  "featured": false,
  "active": true
}
```

**201 Created** + header `Location`, `{ "data": Product }`

#### `PATCH /api/v1/admin/products/{id}`

Body parcial: cualquier subconjunto de los campos. **200** `{ "data": Product }`. Si el producto no existe, `404`.

#### `DELETE /api/v1/admin/products/{id}`

**204**. Si la imagen está en el storage propio, se borra en segundo plano.

**Validaciones**

| Campo | Tipo | Req. | Regla |
|---|---|---|---|
| `name` | string | Sí | trim, 3–80 caracteres |
| `categoryId` | uuid | Sí | Debe existir. Si no existe, `422` en `categoryId` |
| `description` | string | Sí | trim, 10–240 caracteres |
| `size` | string | Sí | trim, 2–60 caracteres |
| `priceFrom` | number | Sí | > 0, ≤ 10000, máximo 2 decimales |
| `image` | string | Sí | URL `https://` de `publicUrl` devuelta por `POST /admin/uploads/sign` (bucket público). Debe empezar con `{SUPABASE_URL}/storage/v1/object/public/{bucket}/` |
| `featured` | boolean | Sí | Default `false` |
| `active` | boolean | Sí | Default `true` |

---

### 2.3 Categorías

**Objeto `Category`**

```json
{
  "id": "c1a7…",
  "name": "Tortas clásicas",
  "slug": "tortas-clasicas",
  "description": "Nuestros sabores de siempre, para cualquier ocasión.",
  "active": true,
  "productCount": 6,
  "createdAt": "2026-09-01T14:00:00.000Z",
  "updatedAt": "2026-09-01T14:00:00.000Z"
}
```

| Endpoint | Notas |
|---|---|
| `GET /categories` | Solo las activas, ordenadas por `name`. Omite `productCount` y la auditoría |
| `GET /admin/categories` | `q` (busca en name/slug), `status`, `page`, `pageSize`, `sort` (`name`, `updatedAt`) |
| `POST /admin/categories` | Si el `slug` ya existe, `409 CONFLICT` con `details.slug` |
| `PATCH /admin/categories/{id}` | Parcial. Si se desactiva, sus productos dejan de verse en la landing |
| `DELETE /admin/categories/{id}` | Si tiene productos, `409 CONFLICT` ("Mueve o elimina sus 6 productos primero") |

**Body (POST)**

```json
{ "name": "Bocaditos salados", "slug": "bocaditos-salados", "description": "Empanaditas, tequeños y causitas.", "active": true }
```

| Campo | Req. | Regla |
|---|---|---|
| `name` | Sí | trim, 3–50 caracteres |
| `slug` | Sí | 2–50 caracteres, `^[a-z0-9]+(?:-[a-z0-9]+)*$`, único (sin distinguir mayúsculas) |
| `description` | No | trim, 0–160 caracteres, default `""` |
| `active` | Sí | boolean |

---

### 2.4 Testimonios

**Objeto `Testimonial`**

```json
{
  "id": "t9e2…",
  "name": "Lucía Pérez",
  "occasion": "Cumpleaños de mi hija",
  "rating": 5,
  "text": "La torta llegó perfecta y el sabor encantó a todos. ¡Ya estoy pidiendo la próxima!",
  "image": "https://cdn.abejitacakes.pe/testimonials/lucia-2b7c.webp",
  "published": true,
  "createdAt": "2026-10-02T12:00:00.000Z",
  "updatedAt": "2026-10-02T12:00:00.000Z"
}
```

| Endpoint | Notas |
|---|---|
| `GET /testimonials` | Solo `published = true`, orden `-createdAt`, `limit` (default 6, máximo 20) |
| `GET /admin/testimonials` | `q` (busca en name/occasion/text), `status` = `published` \| `draft` \| `all`, `rating` (1–5), paginación, `sort` (`createdAt`, `rating`, `name`) |
| `POST` / `PATCH` / `DELETE` | Igual que productos |

| Campo | Req. | Regla |
|---|---|---|
| `name` | Sí | trim, 3–60 caracteres |
| `occasion` | Sí | trim, 3–60 caracteres |
| `rating` | Sí | entero 1–5 |
| `text` | Sí | trim, 20–400 caracteres. Se guarda como texto plano (sin HTML) |
| `image` | No | `null` o `publicUrl` del bucket (misma regla que en productos). Si es `null`, la UI muestra la inicial del nombre |
| `published` | Sí | boolean |

---

### 2.5 FAQs

**Objeto `Faq`**

```json
{
  "id": "f3b1…",
  "question": "¿Con cuánta anticipación debo hacer mi pedido?",
  "answer": "Recomendamos 3 días para tortas clásicas y 7 días para tortas temáticas o de boda.",
  "order": 1,
  "published": true,
  "createdAt": "2026-09-01T14:00:00.000Z",
  "updatedAt": "2026-09-15T09:10:00.000Z"
}
```

| Endpoint | Notas |
|---|---|
| `GET /faqs` | Solo las publicadas, orden `order` ascendente |
| `GET /admin/faqs` | `q` (busca en question/answer), `status` = `published` \| `draft` \| `all`, paginación, `sort` (default `order`) |
| `PUT /admin/faqs/order` | Reordenamiento masivo en una transacción |

**`PUT /admin/faqs/order`**

```json
{ "items": [ { "id": "f3b1…", "order": 1 }, { "id": "a8c2…", "order": 2 } ] }
```

**200** `{ "data": [Faq, …] }`. Si algún ID no existe, `422`.

| Campo | Req. | Regla |
|---|---|---|
| `question` | Sí | trim, 8–140 caracteres |
| `answer` | Sí | trim, 20–600 caracteres |
| `order` | Sí | entero 1–999. Puede repetirse; en caso de empate se ordena por `createdAt` |
| `published` | Sí | boolean |

---

### 2.6 Upload de imágenes

Las imágenes **no pasan por el backend**. El backend firma la subida, el navegador sube el archivo directo a Supabase Storage y luego guarda la URL pública en el registro correspondiente. Así se evitan los límites de tamaño de Render/Vercel y el servidor no procesa binarios.

**Flujo**

```text
Admin (Next.js)                    API (Spring Boot)              Supabase Storage
      │  POST /admin/uploads/sign          │                              │
      │  {folder, contentType, size}       │  valida carpeta/tipo/tamaño  │
      │───────────────────────────────────>│─────────────────────────────>│
      │  {path, uploadUrl, publicUrl}      │                              │
      │<───────────────────────────────────│                              │
      │  PUT uploadUrl (body = archivo)                                   │
      │──────────────────────────────────────────────────────────────────>│
      │  POST/PATCH /admin/{recurso} con image = publicUrl                │
      │───────────────────────────────────>│  valida host y prefijo       │
```

#### `POST /api/v1/admin/uploads/sign`

```json
{ "folder": "products", "contentType": "image/webp", "size": 284512 }
```

| Campo | Req. | Regla |
|---|---|---|
| `folder` | Sí | `products` \| `testimonials` |
| `contentType` | Sí | `image/jpeg`, `image/png` o `image/webp` (se ignoran los parámetros tipo `; charset=…`) |
| `size` | Sí | Bytes del archivo. `> 0` y `≤ 5 MB` |

**201 Created**

```json
{
  "data": {
    "path": "products/8f14e45f-ceea-467f-a9b8-1f2c3d4e5f60.webp",
    "uploadUrl": "https://<proyecto>.supabase.co/storage/v1/object/upload/sign/media/products/8f14e45f-….webp?token=…",
    "publicUrl": "https://<proyecto>.supabase.co/storage/v1/object/public/media/products/8f14e45f-….webp",
    "expiresIn": 300
  }
}
```

- `path` lo genera el backend (`{folder}/{uuid}.{ext}`): el cliente nunca propone el nombre.
- `uploadUrl` es la URL firmada de subida. Es de un solo uso y expira en `expiresIn` segundos (default 300).
- El cliente sube con `PUT <uploadUrl>` y el archivo como cuerpo binario. Debe enviar `Content-Type` con el mismo valor usado en la firma. No se permiten reintentos con la misma URL: si falla, se pide otra firma.
- `publicUrl` es la URL definitiva (bucket público). Se guarda en `image` y, junto con `path`, en `image_path`.

**Errores:** `413 PAYLOAD_TOO_LARGE`, `415 UNSUPPORTED_MEDIA_TYPE`, `422 VALIDATION_ERROR` (folder o size inválidos), `401`, `502 STORAGE_UNAVAILABLE`.

#### `DELETE /api/v1/admin/uploads`

```json
{ "path": "products/8f14e45f-ceea-467f-a9b8-1f2c3d4e5f60.webp" }
```

También acepta `?path=...` para clientes que no envían body en `DELETE`.

**204**. Si algún registro usa esa imagen (`products.image_path` o `testimonials.image_path`), `409 CONFLICT`. Borrar un objeto inexistente es idempotente (`204`). `path` debe respetar `^(products|testimonials)/[a-z0-9-]+\.(jpg|jpeg|png|webp)$`; si no, `422`.

#### Configuración de Supabase Storage

| Requisito | Valor |
|---|---|
| Bucket | `media` (público) |
| `file_size_limit` | 5 MB |
| `allowed_mime_types` | `image/jpeg`, `image/png`, `image/webp` |

Variables de entorno del backend (Render, nunca en el repo):

| Variable | Uso |
|---|---|
| `SUPABASE_URL` | Base del proyecto; ya se usa para validar el JWT |
| `SUPABASE_SERVICE_ROLE_KEY` | Firma las subidas y borra objetos. **Solo backend** |
| `SUPABASE_STORAGE_BUCKET` | Default `media` |
| `SUPABASE_STORAGE_SIGNED_URL_TTL` | Default `300s` |
| `SUPABASE_STORAGE_MAX_FILE_SIZE` | Default `5MB` |

El backend llama a `POST /storage/v1/object/upload/sign/{bucket}/{path}` y `DELETE /storage/v1/object/{bucket}/{path}` con `Authorization: Bearer <service_role>` y `apikey`. La landing consume `publicUrl` directamente, sin firmar.

---

### 2.7 Dashboard

#### `GET /api/v1/admin/stats`

```json
{
  "data": {
    "products": { "total": 14, "active": 12, "featured": 4 },
    "categories": { "total": 4, "active": 4 },
    "testimonials": { "total": 9, "published": 6 },
    "faqs": { "total": 7, "published": 7 }
  }
}
```

---

## 3. Modelo de datos

```text
categories 1 ──── N products
admin_users 1 ──── N (created_by / updated_by en todas las tablas)
testimonials, faqs: independientes
```

### `categories`

| Columna | Tipo | Req. | Notas |
|---|---|---|---|
| `id` | uuid PK | Sí | `gen_random_uuid()` |
| `name` | varchar(50) | Sí | |
| `slug` | varchar(50) | Sí | `UNIQUE (lower(slug))` |
| `description` | varchar(160) | No | default `''` |
| `active` | boolean | Sí | default `true` |

### `products`

| Columna | Tipo | Req. | Notas |
|---|---|---|---|
| `id` | uuid PK | Sí | |
| `category_id` | uuid FK → `categories.id` | Sí | `ON DELETE RESTRICT` |
| `name` | varchar(80) | Sí | |
| `description` | varchar(240) | Sí | |
| `size` | varchar(60) | Sí | |
| `price_from` | numeric(10,2) | Sí | `CHECK (price_from > 0)` |
| `image_url` | text | Sí | |
| `image_path` | text | No | Clave en el storage, para poder borrarla |
| `featured` | boolean | Sí | default `false` |
| `active` | boolean | Sí | default `true` |
| Índices | | | `(category_id)`, `(active, featured)`, índice trigram en `name` |

### `testimonials`

| Columna | Tipo | Req. | Notas |
|---|---|---|---|
| `id` | uuid PK | Sí | |
| `name` | varchar(60) | Sí | |
| `occasion` | varchar(60) | Sí | |
| `rating` | smallint | Sí | `CHECK (rating BETWEEN 1 AND 5)` |
| `text` | varchar(400) | Sí | |
| `image_url` | text | No | |
| `image_path` | text | No | |
| `published` | boolean | Sí | default `false` |

### `faqs`

| Columna | Tipo | Req. | Notas |
|---|---|---|---|
| `id` | uuid PK | Sí | |
| `question` | varchar(140) | Sí | |
| `answer` | varchar(600) | Sí | |
| `sort_order` | smallint | Sí | `CHECK (sort_order BETWEEN 1 AND 999)`. Se expone como `order` |
| `published` | boolean | Sí | default `true` |


### Estados

| Recurso | Campo | Efecto en la landing |
|---|---|---|
| Producto | `active` | Si es `false`, no aparece |
| Producto | `featured` | Aparece primero o en la sección de destacados |
| Categoría | `active` | Si es `false`, se ocultan la categoría, su filtro y todos sus productos |
| Testimonio | `published` | Si es `false`, queda como borrador |
| FAQ | `published` | Si es `false`, queda como borrador |

---
