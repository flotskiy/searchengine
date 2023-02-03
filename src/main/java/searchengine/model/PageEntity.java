package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "Pages",
        indexes = @Index(name = "path_index", columnList = "path"),
        uniqueConstraints = { @UniqueConstraint(columnNames = { "path", "site_id" }) }
)
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    @ManyToOne
    @JoinColumn(name = "site_id", referencedColumnName = "id", nullable = false)
    private SiteEntity siteEntity;

    @OneToMany(mappedBy = "pageId", cascade = CascadeType.ALL)
    private List<IndexEntity> indexEntities;
}
