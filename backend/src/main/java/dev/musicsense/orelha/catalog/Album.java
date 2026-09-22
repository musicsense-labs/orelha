package dev.musicsense.orelha.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "album")
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Column(nullable = false)
    private String title;

    /** O que as tags disseram: a edição que está no disco (o box set de 2009, por exemplo). */
    private Integer year;

    /** MusicBrainz: release-group do álbum. */
    private String mbid;

    /** Ano da primeira edição do release-group — é este que vale como era nas métricas. */
    @Column(name = "first_released")
    private Integer firstReleased;

    @Enumerated(EnumType.STRING)
    @Column(name = "metadata_source", nullable = false)
    private MetadataSource metadataSource = MetadataSource.TAGS;

    protected Album() {
    }

    public Album(Artist artist, String title, Integer year) {
        this.artist = artist;
        this.title = title;
        this.year = year;
    }

    public Long getId() {
        return id;
    }

    public Artist getArtist() {
        return artist;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getMbid() {
        return mbid;
    }

    public Integer getFirstReleased() {
        return firstReleased;
    }

    public MetadataSource getMetadataSource() {
        return metadataSource;
    }

    /** O ano que vale como era: a primeira edição do release-group, ou o da tag enquanto ninguém identificou. */
    public Integer effectiveYear() {
        return firstReleased != null ? firstReleased : year;
    }

    /** Identificação aceita pelo dono; o ano da tag (a edição) fica onde está. */
    public void identify(String mbid, Integer firstReleased) {
        this.mbid = mbid;
        this.firstReleased = firstReleased;
        this.metadataSource = MetadataSource.MUSICBRAINZ;
    }

    public void forget() {
        this.mbid = null;
        this.firstReleased = null;
        this.metadataSource = MetadataSource.TAGS;
    }
}
