package searchengine.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode
@Entity
@Table(name = "Lemmas")
public class LemmaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false)
    private String lemma;

    @Column(nullable = false)
    private int frequency;

    @ManyToOne
    @JoinColumn(name = "site_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SiteEntity siteId;

    @OneToMany(mappedBy = "lemmaId", cascade = CascadeType.ALL)
    private List<IndexEntity> indexEntities;
}
