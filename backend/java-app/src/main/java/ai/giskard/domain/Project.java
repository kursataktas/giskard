package ai.giskard.domain;

import ai.giskard.domain.ml.Dataset;
import ai.giskard.domain.ml.ProjectModel;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Entity(name = "projects")
@NoArgsConstructor
public class Project {
    @Getter
    @Setter
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotNull
    private String key;

    @Getter
    @Setter
    @NotNull
    private String name;
    @Getter
    @Setter
    private String description;

    @Getter
    @Setter
    private LocalDateTime localDateTime;

    @Getter
    @Setter
    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<ProjectModel> models;

    @Getter
    @Setter
    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Dataset> datasets;

    @Getter
    @Setter
    @ManyToMany
    @JoinTable(
        name = "projects_guests",
        joinColumns = @JoinColumn(name = "project_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id"))
    @Delegate
    private List<User> users;

    public Project(String key, String name, String description, LocalDateTime localDateTime) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.localDateTime = localDateTime;
    }

    //    class Project(Base):
//    __tablename__ = "projects"
//
    //    id = Column(Integer, primary_key=True, index=True)
    //    key = Column(String, index=True, unique=True, nullable=False)
    //    name = Column(String, nullable=False)
    //    description = Column(String)
//    created_on = Column(DateTime(timezone=True), default=datetime.datetime.now)
//    owner_id = Column(Integer, ForeignKey("user.id"), nullable=False)
//    owner_details = relationship("User")
//    guest_list = relationship("User", secondary=association_table)
//    model_files = relationship("ProjectModel", cascade="all, delete")
//    data_files = relationship("Dataset", cascade="all, delete")
}
