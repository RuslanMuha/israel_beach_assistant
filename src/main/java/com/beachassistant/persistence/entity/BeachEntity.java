package com.beachassistant.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "beach")
@Getter
@Setter
@NoArgsConstructor
public class BeachEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private CityEntity city;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false, unique = true)
    private String slug;

    @ElementCollection
    @CollectionTable(name = "beach_alias", joinColumns = @JoinColumn(name = "beach_id"))
    @Column(name = "alias")
    private List<String> aliases = new ArrayList<>();

    private Double latitude;
    private Double longitude;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "supports_swimming", nullable = false)
    private boolean supportsSwimming = true;

    @Column(name = "has_lifeguards", nullable = false)
    private boolean hasLifeguards = false;

    @Column(name = "has_camera", nullable = false)
    private boolean hasCamera = false;

    @Column(name = "has_jellyfish_source", nullable = false)
    private boolean hasJellyfishSource = false;

    private String notes;

    @Column(name = "profile_json", columnDefinition = "TEXT")
    private String profileJson;
}
