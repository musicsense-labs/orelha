-- track.audio_path becomes relative to orelha.library.dir for files inside the library
-- (default ../data/audio), with '/' as separator. Files registered by path elsewhere stay absolute.
-- Assumes the default library layout <repo>/data/audio/<albumId>/<file>; that is the only one ever used.

UPDATE track
SET audio_path = replace(regexp_replace(audio_path, '^.*[\\/]data[\\/]audio[\\/]', ''), '\', '/')
WHERE audio_path ~ '[\\/]data[\\/]audio[\\/]';
