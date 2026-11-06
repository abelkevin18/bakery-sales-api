-- Modelo de datos de Abejita Cakes (PostgreSQL).

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

DO $$
BEGIN
    CREATE TYPE admin_role AS ENUM ('owner', 'editor');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END
$$;

CREATE TABLE IF NOT EXISTS admin_users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email citext NOT NULL UNIQUE,
    name varchar(80) NOT NULL,
    role admin_role NOT NULL,
    password_hash text
);

COMMENT ON COLUMN admin_users.id IS
    'En Supabase corresponde a auth.users.id; con autenticación propia puede generarse localmente.';
COMMENT ON COLUMN admin_users.password_hash IS
    'Se utiliza cuando la autenticación es propia; puede quedar NULL si la autenticación la gestiona Supabase.';

CREATE TABLE IF NOT EXISTS categories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(50) NOT NULL,
    slug varchar(50) NOT NULL,
    description varchar(160) NOT NULL DEFAULT '',
    active boolean NOT NULL DEFAULT true
);

CREATE UNIQUE INDEX IF NOT EXISTS categories_slug_lower_uidx
    ON categories (lower(slug));

CREATE TABLE IF NOT EXISTS products (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id uuid NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    name varchar(80) NOT NULL,
    description varchar(240) NOT NULL,
    size varchar(60) NOT NULL,
    price_from numeric(10, 2) NOT NULL CHECK (price_from > 0),
    image_url text NOT NULL,
    image_path text,
    featured boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true
);

CREATE INDEX IF NOT EXISTS products_category_id_idx
    ON products (category_id);
CREATE INDEX IF NOT EXISTS products_active_featured_idx
    ON products (active, featured);
CREATE INDEX IF NOT EXISTS products_name_trgm_idx
    ON products USING gin (name gin_trgm_ops);

CREATE TABLE IF NOT EXISTS testimonials (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(60) NOT NULL,
    occasion varchar(60) NOT NULL,
    rating smallint NOT NULL CHECK (rating BETWEEN 1 AND 5),
    text varchar(400) NOT NULL,
    image_url text,
    image_path text,
    published boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE testimonials
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS testimonials_published_created_idx
    ON testimonials (published, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS testimonials_image_path_idx
    ON testimonials (image_path);
CREATE INDEX IF NOT EXISTS testimonials_name_trgm_idx
    ON testimonials USING gin (name gin_trgm_ops);

CREATE TABLE IF NOT EXISTS faqs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question varchar(140) NOT NULL,
    answer varchar(600) NOT NULL,
    sort_order smallint NOT NULL CHECK (sort_order BETWEEN 1 AND 999),
    published boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE faqs
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
