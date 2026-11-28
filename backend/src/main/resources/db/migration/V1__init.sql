create table book (
	id bigserial primary key,
	title text,
	author text,
	original_filename text not null,
	format varchar(8) not null,
	language varchar(16),
	status varchar(16) not null,
	char_count int not null,
	chunks_total int,
	chunks_done int not null default 0,
	input_tokens bigint not null default 0,
	output_tokens bigint not null default 0,
	cost_usd numeric(12, 6) not null default 0,
	error_message text,
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now()
);

-- Full text is kept apart from book so that listings never load it.
create table book_content (
	book_id bigint primary key references book (id) on delete cascade,
	text text not null
);

create table book_chapter (
	id bigserial primary key,
	book_id bigint not null references book (id) on delete cascade,
	chapter_index int not null,
	title text,
	start_offset int not null,
	end_offset int not null,
	unique (book_id, chapter_index)
);

create table book_character (
	id bigserial primary key,
	book_id bigint not null references book (id) on delete cascade,
	canonical_name text not null,
	first_offset int,
	unique (book_id, canonical_name)
);

create table character_alias (
	id bigserial primary key,
	character_id bigint not null references book_character (id) on delete cascade,
	alias text not null,
	unique (character_id, alias)
);

create table character_observation (
	id bigserial primary key,
	character_id bigint not null references book_character (id) on delete cascade,
	kind varchar(16) not null,
	category varchar(32) not null,
	description text not null,
	quote text not null,
	start_offset int,
	end_offset int,
	chapter_id bigint references book_chapter (id) on delete set null,
	explicit_change boolean not null default false
);

create index ix_book_chapter_book on book_chapter (book_id);
create index ix_book_character_book on book_character (book_id);
create index ix_character_alias_lower on character_alias (lower(alias));
create index ix_character_observation_state on character_observation (character_id, kind, category, start_offset);
