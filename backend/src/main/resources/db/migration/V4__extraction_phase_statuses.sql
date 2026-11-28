-- EXTRACTING is split into EXTRACTING_CHARACTERS and EXTRACTING_SCENES, which do not fit varchar(16).
alter table book alter column status type varchar(32);

-- Keep old rows mappable; startup recovery then marks them FAILED as interrupted.
update book set status = 'EXTRACTING_CHARACTERS' where status = 'EXTRACTING';
