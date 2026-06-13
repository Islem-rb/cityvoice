package tn.cityvoice.personnelservice.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.apache.catalina.User;


import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)

public class CvUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;


    @Column(nullable = false)
    String fileName;

    @Column(nullable = false)
    String fileType;
    @Column(nullable = false)
    UUID userId;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    byte[] data;

    @ManyToOne
    @JoinColumn(name = "candidature_id")
    @JsonBackReference
    CandidatureEquipe candidature;

}
