-- Identidade externa (MusicBrainz, dados CC0): o MBID fixa quem é o artista e qual é o álbum, e o ano da
-- primeira edição do release-group corrige a era — o acervo tinha 17 álbuns dos Beatles datados 2009 (o box
-- set remasterizado) em vez de 1963–70. `year` continua sendo o que veio das tags (a edição que está no
-- disco); `first_released` é o ano do release-group, e é ele que vale nas métricas por era.
-- Sem UNIQUE no mbid de propósito: um box set partido em CD1/CD2 são dois álbuns nossos para um release-group.
ALTER TABLE artist
    ADD COLUMN mbid VARCHAR(36);

ALTER TABLE album
    ADD COLUMN mbid            VARCHAR(36),
    ADD COLUMN first_released  INTEGER,
    ADD COLUMN metadata_source TEXT NOT NULL DEFAULT 'TAGS';   -- TAGS, MUSICBRAINZ, MANUAL

CREATE INDEX album_mbid_idx ON album (mbid);
CREATE INDEX artist_mbid_idx ON artist (mbid);
