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
| 409 | `CONFLICT` | Categoría con productos asociados |
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

**Objeto `Product` (respuesta admin)**

```json
{
  "id": "3f6a2b1e-8c4d-4e9a-9b7f-1a2b3c4d5e6f",
  "name": "Torta Tres Leches",
  "categoryId": "c1a7…",
  "category": { "id": "c1a7…", "name": "Tortas clásicas", "slug": "tortas-clasicas" },
  "description": "Bizcocho húmedo bañado en tres leches con merengue italiano.",
  "size": "20 porciones",
  "priceFrom": 85.00,
  "image": "https://<proyecto>.supabase.co/storage/v1/object/public/media/products/8f14e45f-ceea-467f-a9b8-1f2c3d4e5f60.webp",
  "featured": true,
  "active": true,
  "createdAt": "2026-09-01T14:00:00.000Z",
  "updatedAt": "2026-10-20T18:32:11.000Z"
}
```

`category` viene resuelto por el backend en la misma consulta: el front **nunca** lo envía ni lo necesita para filtrar (para eso está `categoryId`).

#### `GET /products` (público, sin autenticación)

Devuelve solo productos con `active = true` **cuya categoría también está activa**. Sin paginación (el catálogo es chico) y sin `meta`. Orden fijo: destacados primero, luego por nombre ascendente.

| Query | Tipo | Descripción |
|---|---|---|
| `category` | slug | Filtra por el slug de la categoría |
| `featured` | boolean | `true` solo destacados, `false` solo no destacados. Omitido = todos |

```json
{
  "data": [
    {
      "id": "3f6a2b1e-…",
      "name": "Torta Tres Leches",
      "category": { "slug": "tortas-clasicas", "name": "Tortas clásicas" },
      "description": "Bizcocho húmedo bañado en tres leches con merengue italiano.",
      "size": "20 porciones",
      "priceFrom": 85.00,
      "image": "https://<proyecto>.supabase.co/storage/v1/object/public/media/products/…webp",
      "featured": true
    }
  ]
}
```

La vista pública **omite** `active`, `createdAt`, `updatedAt`, `categoryId` y `category.id`. Basta `category.slug` como clave de filtro y `category.name` para pintar la etiqueta.

Si `category` apunta a un slug inexistente o a una categoría inactiva, la respuesta es `{ "data": [] }` (no `404`).

#### `GET /products/{id}`

`{ "data": Product }` con la **misma forma pública** que la lista (sin `active`, `createdAt`, `updatedAt`, `categoryId` ni `category.id`).

Devuelve `404 NOT_FOUND` tanto si el ID no existe como si el producto o su categoría están inactivos: para la landing un producto oculto es indistinguible de uno inexistente. `400 BAD_REQUEST` si no es un UUID válido.

#### `GET /admin/products`

| Query | Tipo | Default | Descripción |
|---|---|---|---|
| `status` | `active` \| `inactive` \| `all` | `all` | Filtro de estado |
| `q` | string | — | 0–100 caracteres. Busca sin distinguir mayúsculas ni acentos en `name` y `description` |
| `categoryId` | uuid | — | Filtro del select de categoría |
| `featured` | boolean | — | `true` / `false` |
| `page` | int | `1` | ≥ 1 |
| `pageSize` | int | `20` | 1–100 |
| `sort` | `name` \| `priceFrom` \| `updatedAt` \| `createdAt` | `-updatedAt` | Prefijo `-` para descendente |

```http
GET /api/v1/admin/products?status=active&categoryId=c1a7…&page=1&pageSize=20&sort=name
```

Devuelve la envoltura de lista (`data` + `meta`) con objetos `Product` completos.

#### `GET /admin/products/{id}`

`{ "data": Product }`. `404 NOT_FOUND` si el ID no existe, `400 BAD_REQUEST` si no es un UUID válido.

#### `POST /admin/products`

```http
POST /api/v1/admin/products
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Torta Selva Negra",
  "categoryId": "c1a7…",
  "description": "Bizcocho de chocolate, crema chantilly y cerezas al marrasquino.",
  "size": "15 porciones",
  "priceFrom": 95,
  "image": "https://<proyecto>.supabase.co/storage/v1/object/public/media/products/2b9c7d10-….webp",
  "featured": false,
  "active": true
}
```

**`201 Created`** + header `Location: /api/v1/admin/products/{id}` y `{ "data": Product }`.

**Todos los campos son obligatorios**, incluidos `featured` y `active` (no hay defaults implícitos: lo que la UI muestre debe viajar en el body).

**`422 VALIDATION_ERROR`** con `details` por campo:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Revisa los campos marcados.",
    "details": {
      "name": ["Debe tener entre 3 y 80 caracteres."],
      "categoryId": ["La categoría seleccionada no existe."],
      "image": ["La imagen debe ser la URL pública devuelta por /admin/uploads/sign."]
    },
    "requestId": "req_01JBX4M8Q2"
  }
}
```

`categoryId` debe existir, pero **puede ser una categoría inactiva**: el admin puede preparar el producto y activar la categoría después. En la landing no se verá hasta que la categoría esté activa.

#### `PATCH /admin/products/{id}`

Parcial: solo se aplican los campos presentes.

| Campo | Regla |
|---|---|
| `name` | trim, 3–80 |
| `categoryId` | Debe existir |
| `description` | trim, 10–240 |
| `size` | trim, 2–60 |
| `priceFrom` | > 0, ≤ 10000, máximo 2 decimales |
| `image` | Ver tabla siguiente |
| `featured` | boolean |
| `active` | boolean |

**Tri-estado de `image` en el PATCH**

| Body | Efecto |
|---|---|
| `image` ausente | La imagen actual no cambia |
| `"image": "https://…/public/media/products/nueva.webp"` | Se reemplaza; la anterior se borra del storage tras el commit |
| `"image": null` | **`422`**: un producto no puede quedarse sin imagen (`image_url` es `NOT NULL`) |
| `"image": ""` | Se trata como ausente |

- Body vacío (`{}`) o con todos los campos en `null` → `422 VALIDATION_ERROR` con `details.body`.
- Body ausente o JSON mal formado → `400 BAD_REQUEST`.
- Producto inexistente → `404 NOT_FOUND`.

Devuelve `200` con el `Product` actualizado.

#### `DELETE /admin/products/{id}`

`204 No Content`. La imagen asociada se borra del storage **después del commit** (best-effort: si el borrado falla solo se registra un `WARN`). `404 NOT_FOUND` si el ID no existe.

#### Validaciones

| Campo | Tipo | Req. POST | Regla |
|---|---|---|---|
| `name` | string | Sí | trim, 3–80 caracteres |
| `categoryId` | uuid | Sí | Debe existir. Si no existe, `422` en `categoryId` |
| `description` | string | Sí | trim, 10–240 caracteres |
| `size` | string | Sí | trim, 2–60 caracteres |
| `priceFrom` | number | Sí | > 0, ≤ 10000, máximo 2 decimales |
| `image` | string | Sí | URL `https://` de `publicUrl` devuelta por `POST /admin/uploads/sign`. Debe empezar con `{SUPABASE_URL}/storage/v1/object/public/{bucket}/`; el backend deriva `image_path` de ahí |
| `featured` | boolean | Sí | Sin default implícito |
| `active` | boolean | Sí | Sin default implícito |

#### Errores propios de productos

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `BAD_REQUEST` | `status`/`sort` inválido, `page`/`pageSize` fuera de rango, `q` > 100 caracteres, UUID mal formado, body ausente |
| 404 | `NOT_FOUND` | El ID no existe |
| 422 | `VALIDATION_ERROR` | Falla la validación del body (`details` por campo) |

#### Integración TypeScript

```ts
export type CategoryRef = { id: string; name: string; slug: string };

export type Product = {
  id: string;
  name: string;
  categoryId: string;
  category: CategoryRef;
  description: string;
  size: string;
  priceFrom: number;
  image: string;
  featured: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PublicProduct = {
  id: string;
  name: string;
  category: { slug: string; name: string };
  description: string;
  size: string;
  priceFrom: number;
  image: string;
  featured: boolean;
};

// Todos los campos son obligatorios en el POST.
export type ProductInput = {
  name: string;
  categoryId: string;
  description: string;
  size: string;
  priceFrom: number;
  image: string;
  featured: boolean;
  active: boolean;
};

export async function listProducts(params: {
  category?: string;
  featured?: boolean;
} = {}): Promise<PublicProduct[]> {
  const search = new URLSearchParams();
  if (params.category) search.set("category", params.category);
  if (params.featured !== undefined) search.set("featured", String(params.featured));

  const res = await fetch(`${API_BASE}/products?${search}`);
  return (await res.json()).data;
}

export async function listAdminProducts(params: {
  status?: "active" | "inactive" | "all";
  q?: string;
  categoryId?: string;
  featured?: boolean;
  page?: number;
  pageSize?: number;
  sort?: "name" | "priceFrom" | "createdAt" | "updatedAt" | `-${string}`;
}) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") search.set(key, String(value));
  });

  const res = await fetch(`${API_BASE}/admin/products?${search}`, { credentials: "include" });
  return res.json() as Promise<{ data: Product[]; meta: PageMeta }>;
}

// PATCH: para reemplazar la imagen manda la nueva URL; NUNCA envíes null (422).
```

---

### 2.3 Categorías

**Objeto `Category` (respuesta admin)**

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

| Campo | Tipo | Notas |
|---|---|---|
| `id` | uuid | |
| `name` | string | 3–50 caracteres |
| `slug` | string | **Solo lectura.** Lo genera el backend; el front nunca lo envía |
| `description` | string | `""` si no se envió |
| `active` | boolean | Si es `false`, la categoría y sus productos desaparecen de la landing |
| `productCount` | int | Ver la tabla de semántica más abajo |
| `createdAt` / `updatedAt` | ISO 8601 | Solo en respuestas admin |

#### ⚠️ El `slug` es inmutable

El `slug` se genera **una sola vez**, al crear la categoría, a partir de `name` (minúsculas, sin acentos, espacios → `-`, máximo 50 caracteres). Después **nunca se recalcula**:

- `POST` ignora cualquier `slug` que venga en el body (aunque lo mandes, se descarta).
- `PATCH` tampoco lo toca: si cambias `name` de `"Tortas clásicas"` a `"Tortas tradicionales"`, el `slug` **sigue siendo `tortas-clasicas`**.
- En el formulario de edición, el campo `slug` debe mostrarse como texto de solo lectura (o directamente oculto), con una nota del tipo "el identificador de URL no cambia al renombrar".

Motivo: el `slug` es la clave estable que usan las URLs públicas, los enlaces compartidos en redes y los filtros (`?categoria=tortas-clasicas`). Si se recalculara al renombrar, todos esos enlaces pasarían a 404 y se perdería el posicionamiento en buscadores.

**Si de verdad hay que corregir un `slug`** (por ejemplo, un error de tipeo en el alta), es una operación manual en la base de datos y debe acompañarse de una redirección 301 desde el `slug` anterior hacia el nuevo. No lo expongas como acción de la UI.

#### Semántica de `productCount`

| Endpoint | Qué cuenta |
|---|---|
| `GET /categories` (público) | Solo productos **activos** |
| `GET /admin/categories` y `GET /admin/categories/{id}` | **Todos** los productos de la categoría |
| `DELETE /admin/categories/{id}` | Todos, para decidir si hay conflicto |

Así la landing nunca muestra "6 productos" para una categoría que solo tiene 2 visibles, y el panel admin no pierde de vista los inactivos.

#### `GET /categories` (público, sin autenticación)

Devuelve solo las categorías con `active = true`, ordenadas por `name` ascendente. **Sin paginación y sin `meta`** (el catálogo tiene pocas categorías y la landing necesita la lista completa para pintar los filtros).

```json
{
  "data": [
    {
      "id": "c1a7…",
      "name": "Tortas clásicas",
      "slug": "tortas-clasicas",
      "description": "Nuestros sabores de siempre, para cualquier ocasión.",
      "active": true,
      "productCount": 6
    }
  ]
}
```

> La vista pública **omite `createdAt` y `updatedAt`**: el front no los necesita y no conviene filtrar metadatos internos.

#### `GET /admin/categories`

| Param | Tipo | Default | Regla |
|---|---|---|---|
| `status` | string | `all` | `active` \| `inactive` \| `all` |
| `q` | string | — | 0–100 caracteres. Busca sin distinguir mayúsculas ni acentos en `name` y `slug` |
| `page` | int | `1` | ≥ 1 |
| `pageSize` | int | `20` | 1–100 |
| `sort` | string | `name` | `name` \| `createdAt` \| `updatedAt`. Prefijo `-` para descendente |

`status` es `all` por defecto (igual que en testimonios y FAQs): el panel admin debe ver también las categorías desactivadas. `sort` por defecto es `name` porque es el orden natural del catálogo.

```http
GET /api/v1/admin/categories?status=active&q=tortas&page=1&pageSize=20&sort=-updatedAt
```

```json
{
  "data": [ { "id": "c1a7…", "name": "Tortas clásicas", "slug": "tortas-clasicas", "description": "…", "active": true, "productCount": 6, "createdAt": "2026-09-01T14:00:00.000Z", "updatedAt": "2026-09-20T09:12:00.000Z" } ],
  "meta": { "page": 1, "pageSize": 20, "total": 1, "totalPages": 1 }
}
```

Un `status` o `sort` fuera de la lista devuelve `400 BAD_REQUEST`.

#### `GET /admin/categories/{id}`

```json
{ "data": { "id": "c1a7…", "name": "Tortas clásicas", "slug": "tortas-clasicas", "description": "…", "active": true, "productCount": 6, "createdAt": "…", "updatedAt": "…" } }
```

Si el ID no existe: `404 NOT_FOUND`. Si el ID no es un UUID válido: `400 BAD_REQUEST`.

#### `POST /admin/categories`

```http
POST /api/v1/admin/categories
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Bocaditos salados",
  "description": "Empanaditas, tequeños y causitas.",
  "active": true
}
```

| Campo | Req. | Regla |
|---|---|---|
| `name` | Sí | trim, 3–50 caracteres. Es la base del `slug` |
| `description` | No | trim, 0–160 caracteres, default `""` |
| `active` | Sí | boolean (`null` u omitido → `422`) |
| `slug` | — | **No se envía.** Si llega, se ignora |

**Respuesta `201 Created`** con header `Location: /api/v1/admin/categories/{id}` y el objeto `Category` en `data`.

**`422 VALIDATION_ERROR`** cuando falta `name` o `active`:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Revisa los campos marcados.",
    "details": {
      "name": ["Debe tener entre 3 y 50 caracteres."],
      "active": ["active es obligatorio."]
    },
    "requestId": "req_01JBX4M8Q2"
  }
}
```

**Colisiones de `slug`**: no son un error. Si `"Tortas clásicas"` ya existe con `tortas-clasicas`, la nueva categoría recibe `tortas-clasicas-2` (y así sucesivamente) y el `201` es normal. El front **debe usar el `slug` que devuelve la API**, no el que calcularía a partir del nombre.

Solo si se agotan los 50 intentos (caso irreal) se devuelve `409 CONFLICT` con `details.slug`.

#### `PATCH /admin/categories/{id}`

Parcial: solo se aplican los campos presentes.

| Campo | Req. | Regla |
|---|---|---|
| `name` | No | trim, 3–50 caracteres. **No cambia el `slug`** |
| `description` | No | trim, 0–160 caracteres. `""` la vacía |
| `active` | No | boolean. `false` la oculta de la landing junto con sus productos |
| `slug` | — | **No se acepta.** Se ignora si llega |

```http
PATCH /api/v1/admin/categories/c1a7…
Content-Type: application/json

{ "active": false }
```

- Body vacío (`{}`) o con todos los campos en `null` → `422 VALIDATION_ERROR` con `details.body`.
- Body ausente o JSON mal formado → `400 BAD_REQUEST`.
- ID inexistente → `404 NOT_FOUND`.

Devuelve `200` con el objeto `Category` actualizado.

> Desactivar una categoría **no** desactiva sus productos en la base de datos: siguen con `active = true`, pero la landing los oculta porque la categoría no se muestra. Es intencional, para que reactivarla restaure el catálogo tal cual estaba.

#### `DELETE /admin/categories/{id}`

- Sin productos asociados → `204 No Content`.
- Con productos → `409 CONFLICT`. La categoría **no** se borra (`ON DELETE RESTRICT`) y el mensaje indica cuántos productos hay que mover o eliminar primero:

```json
{
  "error": {
    "code": "CONFLICT",
    "message": "La categoría tiene productos asociados.",
    "details": {
      "products": ["Mueve o elimina sus 6 productos primero."]
    },
    "requestId": "req_01JBX4M8Q2"
  }
}
```

El conteo del conflicto incluye productos activos e inactivos. ID inexistente → `404 NOT_FOUND`.

#### Errores propios de categorías

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `BAD_REQUEST` | `status`/`sort` inválido, `page`/`pageSize` fuera de rango, `q` > 100 caracteres, UUID mal formado, body ausente |
| 404 | `NOT_FOUND` | El ID no existe |
| 409 | `CONFLICT` | Eliminar una categoría con productos |
| 422 | `VALIDATION_ERROR` | Falla la validación del body (`details` por campo) |

#### Integración TypeScript

```ts
export type Category = {
  id: string;
  name: string;
  slug: string;          // solo lectura, generado por el backend
  description: string;
  active: boolean;
  productCount: number;  // activos en público, total en admin
  createdAt: string;
  updatedAt: string;
};

export type PublicCategory = Omit<Category, "createdAt" | "updatedAt">;

// El slug NO forma parte del payload de escritura.
export type CategoryInput = {
  name: string;
  description?: string;
  active: boolean;
};

export async function listCategories(): Promise<PublicCategory[]> {
  const res = await fetch(`${API_BASE}/categories`);
  const body = await res.json();
  return body.data;
}

export async function listAdminCategories(params: {
  status?: "active" | "inactive" | "all";
  q?: string;
  page?: number;
  pageSize?: number;
  sort?: "name" | "createdAt" | "updatedAt" | `-${string}`;
}) {
  const search = new URLSearchParams();
  if (params.status) search.set("status", params.status);
  if (params.q) search.set("q", params.q);
  if (params.page) search.set("page", String(params.page));
  if (params.pageSize) search.set("pageSize", String(params.pageSize));
  if (params.sort) search.set("sort", params.sort);

  const res = await fetch(`${API_BASE}/admin/categories?${search}`, { credentials: "include" });
  return res.json() as Promise<{ data: Category[]; meta: PageMeta }>;
}

// El 409 de DELETE trae details.products: úsalo para el mensaje del modal.
```

---

### 2.4 Testimonios

**Objeto `Testimonial` (respuesta)**

```json
{
  "id": "a9e20000-0000-4000-8000-000000000001",
  "name": "Lucía Pérez",
  "occasion": "Cumpleaños de mi hija",
  "rating": 5,
  "text": "La torta llegó perfecta y el sabor encantó a todos. ¡Ya estoy pidiendo la próxima!",
  "image": "https://<proyecto>.supabase.co/storage/v1/object/public/media/testimonials/c59b8dc6-77ca-477c-ba48-eb9f54e1919f.jpg",
  "published": true,
  "createdAt": "2026-10-02T12:00:00.000Z",
  "updatedAt": "2026-10-02T12:00:00.000Z"
}
```

| Campo | Tipo | Notas |
|---|---|---|
| `id` | uuid | Solo lectura |
| `name` | string | 3–60 |
| `occasion` | string | 3–60 |
| `rating` | number | entero 1–5 |
| `text` | string | 20–400, texto plano |
| `image` | string \| `null` | `publicUrl` exacta devuelta por `/admin/uploads/sign`, o `null` |
| `published` | boolean | `false` = borrador (no aparece en la landing) |
| `createdAt` / `updatedAt` | ISO 8601 | Solo lectura |

> `image_path` **nunca** se envía ni se recibe en la API: el backend lo deriva del `publicUrl` y lo guarda en la columna `image_path` para poder borrar el objeto del storage cuando deja de usarse.

---

#### `GET /api/v1/testimonials` (público)

Sin autenticación. Devuelve solo `published = true`, orden `createdAt DESC`.

| Query param | Tipo | Default | Regla |
|---|---|---|---|
| `limit` | int | `6` | 1–20. Fuera de rango → `400 BAD_REQUEST` |

**200**

```json
{ "data": [ /* Testimonial, … */ ] }
```

Sin resultados → `{ "data": [] }` (200, no 404). No incluye `meta`: la landing solo necesita el arreglo.

---

#### `GET /api/v1/admin/testimonials` (admin)

| Query param | Tipo | Default | Regla |
|---|---|---|---|
| `status` | string | `all` | `published` \| `draft` \| `all`. Otro valor → `400` |
| `q` | string | — | 0–100 caracteres. Busca en `name`, `occasion` y `text` sin distinguir mayúsculas ni acentos |
| `rating` | int | — | 1–5. Fuera de rango → `400` |
| `page` | int | `1` | ≥ 1 |
| `pageSize` | int | `20` | 1–100 |
| `sort` | string | `-createdAt` | `createdAt`, `rating` o `name`. Prefijo `-` = descendente. Campo no permitido → `400` |

**200**

```json
{
  "data": [ /* Testimonial, … */ ],
  "meta": { "page": 1, "pageSize": 20, "total": 37, "totalPages": 2 }
}
```

---

#### `GET /api/v1/admin/testimonials/{id}` (admin)

**200** `{ "data": Testimonial }` · **404** `NOT_FOUND` si no existe · **400** si `{id}` no es un UUID válido.

---

#### `POST /api/v1/admin/testimonials` (admin)

```json
{
  "name": "Rosario Quispe",
  "occasion": "Cumpleaños de mi hija",
  "rating": 5,
  "text": "La torta de abejita quedó idéntica a la foto que les mandé. Mi hija no quería que la cortáramos. Además, ¡estaba deliciosa!",
  "image": null,
  "published": false
}
```

| Campo | Req. | Regla |
|---|---|---|
| `name` | Sí | trim, 3–60 caracteres |
| `occasion` | Sí | trim, 3–60 caracteres |
| `rating` | Sí | entero 1–5 |
| `text` | Sí | trim, 20–400 caracteres, texto plano |
| `image` | No | `null`, ausente, o `publicUrl` exacta del bucket. Cualquier otra URL → `422` en `details.image` |
| `published` | **Sí** | boolean. Si se omite → `422` en `details.published` |

- El `id` lo genera el servidor; si se envía en el body se ignora.
- `image` es opcional: sin foto, la UI muestra la inicial del nombre.

**201 Created** + `Location: /api/v1/admin/testimonials/{id}`

```json
{ "data": Testimonial }
```

**422** con `details` por campo:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Revisa los campos marcados.",
    "details": {
      "name": ["Debe tener entre 3 y 60 caracteres."],
      "text": ["Debe tener entre 20 y 400 caracteres."],
      "image": ["La imagen debe ser la URL pública devuelta por /admin/uploads/sign o null."]
    },
    "requestId": "req_9c1f…"
  }
}
```

---

#### `PATCH /api/v1/admin/testimonials/{id}` (admin)

Actualización parcial: **solo se modifican los campos presentes**. Debe enviarse al menos uno; si el body llega vacío → `422` en `details.body`.

```json
{ "rating": 4, "published": true }
```

**`image` tiene tres estados** — es la parte que más cuidado requiere en el front:

| Body | Efecto |
|---|---|
| `image` **ausente** | Conserva la imagen actual |
| `"image": null` | Elimina la imagen (el objeto se borra del bucket si nadie más lo usa) |
| `"image": "https://…/media/testimonials/x.webp"` | Reemplaza la imagen (el objeto anterior se borra si nadie más lo usa) |

⚠️ Enviar `"image": ""` (string vacío) **no** limpia la imagen: se interpreta como ausente. Para limpiar hay que enviar `null` explícito. En JS, si el campo se arma desde un formulario, omitir la clave cuando no cambió y usar `null` cuando el usuario quitó la foto.

**200** `{ "data": Testimonial }` · **404** si el ID no existe · **422** si un campo presente es inválido.

---

#### `DELETE /api/v1/admin/testimonials/{id}` (admin)

**204 No Content**. La imagen asociada se borra del bucket (best-effort, después del commit). **404** si el ID no existe.

---

#### Errores de testimonios

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `BAD_REQUEST` | `limit`, `status`, `rating`, `page`, `pageSize` o `sort` inválidos; `{id}` no es UUID; JSON mal formado |
| 401 | `UNAUTHENTICATED` | Falta el JWT o expiró (solo rutas `/admin/**`) |
| 404 | `NOT_FOUND` | El testimonio no existe |
| 422 | `VALIDATION_ERROR` | Body inválido, `image` con URL ajena al bucket, o PATCH sin campos |
| 500 | `INTERNAL_ERROR` | Error inesperado |

#### Ejemplo de integración (TypeScript)

```ts
const API = process.env.NEXT_PUBLIC_API_BASE_URL; // "" si es mismo origen

// Landing: solo publicados, sin token
const { data: testimonials } = await fetch(`${API}/api/v1/testimonials?limit=6`)
  .then((r) => r.json());

// Admin: crear (image es opcional)
await fetch(`${API}/api/v1/admin/testimonials`, {
  method: "POST",
  headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
  body: JSON.stringify({
    name, occasion, rating, text,
    image: uploadedPublicUrl ?? null, // o omitir la clave
    published,
  }),
});

// Admin: PATCH — solo las claves que cambiaron
const body: Record<string, unknown> = {};
if (nameChanged) body.name = name;
if (imageRemoved) body.image = null;            // limpia la foto
else if (newUpload) body.image = newUpload;     // reemplaza la foto
// si la foto no cambió: NO incluir la clave "image"
await fetch(`${API}/api/v1/admin/testimonials/${id}`, {
  method: "PATCH",
  headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
  body: JSON.stringify(body),
});
```

Con `image === null` la UI debe mostrar la inicial de `name` en lugar de una foto rota.

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
- `uploadUrl` es la URL firmada de subida. Es válida hasta que expira (`expiresIn` segundos, default 300) y sirve para **una sola** subida: una vez creado el objeto, un segundo `PUT` a la misma ruta falla porque el objeto ya existe. Si la subida falla, se pide una firma nueva.
- El cliente sube con `PUT <uploadUrl>` y el archivo como cuerpo binario. Debe enviar `Content-Type` con el mismo valor usado en la firma. No se permiten reintentos con la misma URL: si falla, se pide otra firma.
- `publicUrl` es la URL definitiva (bucket público). Se guarda en `image` y, junto con `path`, en `image_path`.

**Errores:** `413 PAYLOAD_TOO_LARGE`, `415 UNSUPPORTED_MEDIA_TYPE`, `422 VALIDATION_ERROR` (folder o size inválidos), `401`, `502 STORAGE_UNAVAILABLE`.

#### `DELETE /api/v1/admin/uploads`

```json
{ "path": "products/8f14e45f-ceea-467f-a9b8-1f2c3d4e5f60.webp" }
```

También acepta `?path=...` para clientes que no envían body en `DELETE`.

**204**. Si algún registro usa esa imagen (`products.image_path` o `testimonials.image_path`), `409 CONFLICT`. Borrar un objeto inexistente es idempotente (`204`). `path` debe respetar `^(products|testimonials)/[a-z0-9-]+\.(jpg|jpeg|png|webp)$`; si no, `422`.

## 2.7 NOTAS
#### 2.7.1 Limpieza automática de imágenes huérfanas

El front **no** necesita borrar imágenes al editar o eliminar registros: el backend ya lo hace.

| Acción | Qué borra el backend |
|---|---|
| `PATCH` con `image` distinto (reemplazo) | El objeto anterior, tras el commit y solo si ningún registro lo referencia |
| `PATCH` con `"image": null` | El objeto anterior, con la misma regla |
| `PATCH` con `image` ausente | Nada |
| `DELETE` del registro | Su imagen, tras el commit |

Es *best-effort*: si Supabase Storage falla, la operación de negocio se confirma igual y el objeto queda como huérfano (se registra un `WARN`). Esos casos se limpian a mano con `DELETE /api/v1/admin/uploads`. `DELETE /admin/uploads` sigue siendo útil para descartar una imagen subida que nunca se asoció a ningún registro.

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
testimonials, faqs: independientes
```

Todas las tablas de contenido llevan `created_at` y `updated_at` (`timestamptz`, default `now()`); `updated_at` se refresca en cada `PATCH` desde el backend.

### `categories`

| Columna | Tipo | Req. | Notas |
|---|---|---|---|
| `id` | uuid PK | Sí | `gen_random_uuid()` |
| `name` | varchar(50) | Sí | |
| `slug` | varchar(50) | Sí | `UNIQUE (lower(slug))`. Lo genera el backend al crear y **no se recalcula nunca** |
| `description` | varchar(160) | No | default `''` |
| `active` | boolean | Sí | default `true` |
| `created_at` | timestamptz | Sí | default `now()` |
| `updated_at` | timestamptz | Sí | default `now()`, se actualiza en cada `PATCH` |
| Índices | | | `UNIQUE (lower(slug))`, `(active, name)` |

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
| `created_at` | timestamptz | Sí | default `now()` |
| `updated_at` | timestamptz | Sí | default `now()`, se actualiza en cada `PATCH` |
| Índices | | | `(category_id)`, `(active, featured)`, índice trigram en `name` |

### `testimonials`

| Columna | Tipo | Req. | Notas |
|---|---|---|---|
| `id` | uuid PK | Sí | |
| `name` | varchar(60) | Sí | |
| `occasion` | varchar(60) | Sí | |
| `rating` | smallint | Sí | `CHECK (rating BETWEEN 1 AND 5)` |
| `text` | varchar(400) | Sí | |
| `image_url` | text | No | URL pública del bucket |
| `image_path` | text | No | Clave del objeto en el storage. La deriva el backend; índice para la verificación de uso |
| `published` | boolean | Sí | default `false` |
| `created_at` | timestamptz | Sí | default `now()` |
| `updated_at` | timestamptz | Sí | default `now()`, se actualiza en cada `PATCH` |
| Índices | | | `(published, created_at DESC, id DESC)`, `(image_path)`, trigram en `name` |

### `faqs`

| Columna | Tipo | Req. | Notas |
|---|---|---|---|
| `id` | uuid PK | Sí | |
| `question` | varchar(140) | Sí | |
| `answer` | varchar(600) | Sí | |
| `sort_order` | smallint | Sí | `CHECK (sort_order BETWEEN 1 AND 999)`. Se expone como `order` |
| `published` | boolean | Sí | default `true` |
| `created_at` | timestamptz | Sí | default `now()` |
| `updated_at` | timestamptz | Sí | default `now()`, se actualiza en cada `PATCH` |


### Estados

| Recurso | Campo | Efecto en la landing |
|---|---|---|
| Producto | `active` | Si es `false`, no aparece |
| Producto | `featured` | Aparece primero o en la sección de destacados |
| Categoría | `active` | Si es `false`, se ocultan la categoría, su filtro y todos sus productos |
| Testimonio | `published` | Si es `false`, queda como borrador |
| FAQ | `published` | Si es `false`, queda como borrador |

---
