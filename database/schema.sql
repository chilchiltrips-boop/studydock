-- StudyDock future cloud schema for PostgreSQL / Supabase.
-- Not deployed by this prototype. Requires Supabase Auth and admin role management.
create extension if not exists pgcrypto;
create table if not exists public.books (
  id uuid primary key default gen_random_uuid(), title text not null,
  total_pages integer not null check(total_pages > 0), parts_expected integer not null default 8,
  created_at timestamptz not null default now()
);
create table if not exists public.book_parts (
  id uuid primary key default gen_random_uuid(), book_id uuid not null references public.books(id) on delete cascade,
  part_number integer not null check(part_number between 1 and 8),
  first_page integer, last_page integer,
  status text not null default 'pending' check(status in ('pending','extracted','reviewing','published')),
  source_sha256 text, imported_at timestamptz,
  unique(book_id,part_number), check(first_page is null or last_page is null or first_page <= last_page)
);
create table if not exists public.chapters (
  id uuid primary key default gen_random_uuid(), book_id uuid not null references public.books(id) on delete cascade,
  external_id text not null, chapter_number integer, title text not null, subject text,
  description text, first_page integer, last_page integer,
  unique(book_id,external_id)
);
create table if not exists public.topics (
  id uuid primary key default gen_random_uuid(), chapter_id uuid not null references public.chapters(id) on delete cascade,
  external_id text not null, title text not null, sort_order integer default 0,
  unique(chapter_id,external_id)
);
create table if not exists public.assets (
  id uuid primary key default gen_random_uuid(), book_id uuid not null references public.books(id) on delete cascade,
  external_id text not null, storage_path text not null, alt_text text,
  origin text not null default 'textbook' check(origin in ('textbook','generated','demo')),
  source_page integer, sha256 text,
  verification_status text not null default 'unverified' check(verification_status in ('unverified','verified','rejected')),
  unique(book_id,external_id)
);
create table if not exists public.notes (
  id uuid primary key default gen_random_uuid(), chapter_id uuid not null references public.chapters(id) on delete cascade,
  topic_id uuid references public.topics(id) on delete set null,
  external_id text not null, title text not null, body_markdown text not null,
  source_page integer, status text not null default 'draft' check(status in ('draft','reviewing','published')),
  unique(chapter_id,external_id)
);
create table if not exists public.note_assets (
  note_id uuid not null references public.notes(id) on delete cascade,
  asset_id uuid not null references public.assets(id) on delete cascade,
  position integer not null default 0, primary key(note_id,asset_id)
);
create table if not exists public.questions (
  id uuid primary key default gen_random_uuid(), chapter_id uuid not null references public.chapters(id) on delete cascade,
  topic_id uuid references public.topics(id) on delete set null,
  part_id uuid references public.book_parts(id) on delete set null,
  external_id text not null, stem text not null,
  question_type text not null check(question_type in ('mcq','image')),
  difficulty text check(difficulty in ('easy','medium','hard')),
  source_page integer, source_question_number text,
  origin text not null default 'textbook' check(origin in ('textbook','generated','demo')),
  verification_status text not null default 'unverified' check(verification_status in ('unverified','verified','rejected')),
  explanation text, hint text, created_at timestamptz not null default now(),
  unique(chapter_id,external_id)
);
create table if not exists public.question_options (
  id uuid primary key default gen_random_uuid(), question_id uuid not null references public.questions(id) on delete cascade,
  option_key text not null check(option_key in ('A','B','C','D')),
  text text not null, is_correct boolean not null default false,
  unique(question_id,option_key)
);
create table if not exists public.question_assets (
  question_id uuid not null references public.questions(id) on delete cascade,
  asset_id uuid not null references public.assets(id) on delete cascade,
  position integer not null default 0, primary key(question_id,asset_id)
);
create table if not exists public.user_roles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  role text not null default 'student' check(role in ('student','teacher','admin'))
);
create table if not exists public.quiz_attempts (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
  book_id uuid not null references public.books(id) on delete cascade,
  mode text not null default 'practice', started_at timestamptz not null default now(),
  completed_at timestamptz, total integer not null check(total >= 0), correct integer check(correct >= 0 and correct <= total)
);
create table if not exists public.quiz_answers (
  attempt_id uuid not null references public.quiz_attempts(id) on delete cascade,
  question_id uuid not null references public.questions(id) on delete cascade,
  selected_option text check(selected_option in ('A','B','C','D')),
  is_correct boolean, primary key(attempt_id,question_id)
);
create index if not exists idx_questions_chapter_verified on public.questions(chapter_id,verification_status);
create index if not exists idx_notes_chapter_status on public.notes(chapter_id,status);
create index if not exists idx_attempts_user_time on public.quiz_attempts(user_id,started_at desc);

-- RLS baseline: students only see verified/published learning data and their own attempts.
-- Admin mutation is intentionally NOT allowed via direct browser database grants.
-- Build authenticated, server-side import/review endpoints for those operations.
alter table public.books enable row level security;
alter table public.book_parts enable row level security;
alter table public.chapters enable row level security;
alter table public.topics enable row level security;
alter table public.assets enable row level security;
alter table public.notes enable row level security;
alter table public.note_assets enable row level security;
alter table public.questions enable row level security;
alter table public.question_options enable row level security;
alter table public.question_assets enable row level security;
alter table public.user_roles enable row level security;
alter table public.quiz_attempts enable row level security;
alter table public.quiz_answers enable row level security;

create policy books_authenticated_read on public.books for select to authenticated using (true);
create policy chapters_authenticated_read on public.chapters for select to authenticated using (true);
create policy topics_authenticated_read on public.topics for select to authenticated using (true);
create policy parts_authenticated_read on public.book_parts for select to authenticated using (true);
create policy assets_verified_read on public.assets for select to authenticated using (verification_status='verified');
create policy notes_published_read on public.notes for select to authenticated using (status='published');
create policy note_assets_published_read on public.note_assets for select to authenticated
  using(exists(select 1 from public.notes n where n.id=note_id and n.status='published'));
create policy questions_verified_read on public.questions for select to authenticated using (verification_status='verified');
create policy question_assets_verified_read on public.question_assets for select to authenticated
  using(exists(select 1 from public.questions q where q.id=question_id and q.verification_status='verified'));
-- Do not expose is_correct in client querying an exam answer pool.
-- In production, expose student-safe options via a server API/view and compute scores server-side.
create policy user_roles_own_read on public.user_roles for select to authenticated using(user_id=auth.uid());
create policy attempts_own_read on public.quiz_attempts for select to authenticated using(user_id=auth.uid());
create policy quiz_answers_own_read on public.quiz_answers for select to authenticated
  using(exists(select 1 from public.quiz_attempts a where a.id=attempt_id and a.user_id=auth.uid()));
