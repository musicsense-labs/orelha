package dev.musicsense.orelha.collection;

import dev.musicsense.orelha.catalog.AlbumRepository;
import dev.musicsense.orelha.catalog.ArtistRepository;
import dev.musicsense.orelha.catalog.Track;
import dev.musicsense.orelha.catalog.TrackRepository;
import dev.musicsense.orelha.common.NotFoundException;
import dev.musicsense.orelha.collection.CollectionMetrics.PedalPassage;
import dev.musicsense.orelha.harmony.HarmonicNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CollectionService {

    public record Distances(double transitionJsBits, double degreeL1, double keyRelationL1) {
    }

    public record Comparison(HarmonicProfile a, HarmonicProfile b, Distances distances) {
    }

    private final ArtistRepository artists;
    private final AlbumRepository albums;
    private final TrackRepository tracks;
    private final CollectionQueries queries;

    CollectionService(ArtistRepository artists, AlbumRepository albums, TrackRepository tracks, CollectionQueries queries) {
        this.artists = artists;
        this.albums = albums;
        this.tracks = tracks;
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public HarmonicProfile artistProfile(Long artistId) {
        String name = artists.findById(artistId).orElseThrow(() -> new NotFoundException("Artist", artistId)).getName();
        return profile("artist", artistId, name, tracks.findByAlbumArtistId(artistId));
    }

    @Transactional(readOnly = true)
    public HarmonicProfile albumProfile(Long albumId) {
        String title = albums.findById(albumId).orElseThrow(() -> new NotFoundException("Album", albumId)).getTitle();
        return profile("album", albumId, title, tracks.findByAlbumIdOrderByTrackNoAsc(albumId));
    }

    @Transactional(readOnly = true)
    public Comparison compareArtists(Long a, Long b) {
        HarmonicProfile pa = artistProfile(a);
        HarmonicProfile pb = artistProfile(b);
        return new Comparison(pa, pb, new Distances(
                CollectionMetrics.jensenShannonBits(pa.transitions().flattened(), pb.transitions().flattened()),
                CollectionMetrics.l1(pa.degrees().bySegment(), pb.degrees().bySegment()),
                CollectionMetrics.l1(pa.keyRelations(), pb.keyRelations())));
    }

    @Transactional(readOnly = true)
    public List<PedalPassage> artistPedalPassages(Long artistId, String relation) {
        artists.findById(artistId).orElseThrow(() -> new NotFoundException("Artist", artistId));
        return CollectionMetrics.pedalPassages(annotated(tracks.findByAlbumArtistId(artistId)), relation);
    }

    private HarmonicProfile profile(String scope, Long id, String name, List<Track> scopeTracks) {
        List<Long> ids = scopeTracks.stream().map(Track::getId).toList();
        List<AnnotatedSegment> segments = annotated(scopeTracks);
        int analysedTracks = (int) segments.stream().mapToLong(AnnotatedSegment::trackId).distinct().count();
        double duration = segments.stream().mapToDouble(AnnotatedSegment::duration).sum();
        return new HarmonicProfile(scope, id, name, analysedTracks, segments.size(), duration,
                CollectionMetrics.keyRelationShares(segments), CollectionMetrics.nonDiatonic(segments),
                CollectionMetrics.DEGREE_LABELS, CollectionMetrics.degrees(segments), CollectionMetrics.transitions(segments),
                CollectionMetrics.relationShares(segments), queries.timbreByAlbum(ids));
    }

    private List<AnnotatedSegment> annotated(List<Track> scopeTracks) {
        return queries.annotatedSegments(scopeTracks.stream().map(Track::getId).toList(), HarmonicNormalizer.VERSION);
    }
}
