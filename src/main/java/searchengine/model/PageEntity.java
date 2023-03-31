package searchengine.model;

import lombok.*;

import javax.persistence.*;
import java.util.List;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "Pages",
        indexes = @Index(name = "path_index", columnList = "path"),
        uniqueConstraints = { @UniqueConstraint(columnNames = { "path", "site_id" }) }
)
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @NonNull
    @Column(nullable = false, columnDefinition = "varchar(765)")
    private String path;

    @NonNull
    @Column(nullable = false)
    private int code;

    @NonNull
    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    @NonNull
    @ManyToOne
    @JoinColumn(name = "site_id", referencedColumnName = "id", nullable = false)
    private SiteEntity siteEntity;

    @OneToMany(mappedBy = "pageId", cascade = CascadeType.ALL)
    private List<IndexEntity> indexEntities;
}
