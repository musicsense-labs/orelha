package dev.musicsense.orelha.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "artist")
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String country;

    @Column(name = "formed_year")
    private Integer formedYear;

    /** MusicBrainz: o artista; fixa a identidade que o nome sozinho não fixa ("The Beatles" × "Beatles"). */
    private String mbid;

    protected Artist() {
    }

    public Artist(String name, String country, Integer formedYear) {
        this.name = name;
        this.country = country;
        this.formedYear = formedYear;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Integer getFormedYear() {
        return formedYear;
    }

    public String getMbid() {
        return mbid;
    }

    public void setMbid(String mbid) {
        this.mbid = mbid;
    }

    public void setFormedYear(Integer formedYear) {
        this.formedYear = formedYear;
    }
}
