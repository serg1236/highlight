alter table character_observation add column inferred boolean not null default false;

create table book_location (
	id bigserial primary key,
	book_id bigint not null references book (id) on delete cascade,
	name text not null,
	visual_description text,
	first_offset int,
	unique (book_id, name)
);

create table book_scene (
	id bigserial primary key,
	book_id bigint not null references book (id) on delete cascade,
	scene_index int not null,
	chapter_id bigint references book_chapter (id) on delete set null,
	start_offset int,
	end_offset int,
	summary text not null,
	has_dialog boolean not null,
	location_id bigint references book_location (id) on delete set null,
	location_quote text,
	time_of_day varchar(16) not null,
	time_marker text,
	timeline_shift boolean not null default false,
	timeline_note text,
	unique (book_id, scene_index)
);

create table scene_character (
	id bigserial primary key,
	scene_id bigint not null references book_scene (id) on delete cascade,
	character_id bigint not null references book_character (id) on delete cascade,
	speaks boolean not null,
	unique (scene_id, character_id)
);

create table book_setting (
	book_id bigint primary key references book (id) on delete cascade,
	realism varchar(32) not null,
	genre text,
	period text,
	period_source varchar(16) not null,
	region text,
	technology text,
	clothing_norms text,
	architecture text,
	visual_mood text,
	prompt_line text not null
);

create table setting_clue (
	id bigserial primary key,
	book_id bigint not null references book (id) on delete cascade,
	category varchar(32) not null,
	description text not null,
	quote text not null,
	start_offset int,
	end_offset int,
	chapter_id bigint references book_chapter (id) on delete set null
);

create index ix_book_location_book on book_location (book_id);
create index ix_book_scene_book on book_scene (book_id);
create index ix_book_scene_location on book_scene (location_id);
create index ix_scene_character_character on scene_character (character_id);
create index ix_setting_clue_book on setting_clue (book_id);
