package dev.rifflab.corpus;

import dev.rifflab.corpus.CorpusMetrics.DegreeDistribution;
import dev.rifflab.corpus.CorpusMetrics.Share;
import dev.rifflab.corpus.CorpusMetrics.TransitionMatrix;

import java.util.List;
import java.util.Map;

/** Perfil harmônico agregado de um escopo (artista ou álbum), sobre os runs canônicos. */
public record HarmonicProfile(String scope, Long id, String name, int tracks, int segments, double durationS,
                              Map<String, Share> keyRelations, Share nonDiatonic, List<String> degreeLabels,
                              DegreeDistribution degrees, TransitionMatrix transitions, Map<String, Share> relations,
                              List<AlbumTimbre> timbreByAlbum) {

    /** Média dos agregados tímbricos de um stem nas faixas de um álbum. */
    public record AlbumTimbre(long albumId, String albumTitle, Integer albumYear, String stem, int tracks,
                              Double centroidMean, Double centroidStd, Double flatnessMean, Double rolloffP95,
                              Double rmsMean) {
    }
}
