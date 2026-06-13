package tn.cityvoice.personnelservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CV {
    @Id
    @GeneratedValue
    UUID id;
    @Column
     String competences;
    @Column
    String Diplome;
    @Column
    String Experience;





}
