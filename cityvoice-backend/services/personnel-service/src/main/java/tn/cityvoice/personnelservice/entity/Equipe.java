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
public class Equipe {
    @Id
    @GeneratedValue

    UUID id;
    @Column(nullable = false,unique=true)
    String name;
    @Column(nullable = false)
    String specialite;
    @Column
    String Gouvernorat;
    @Enumerated(EnumType.STRING)
    Etat etat;
    @OneToMany(cascade = CascadeType.ALL)
    List<MembreEquipe> MembresEquipe;



    @PrePersist
    public void prePersist() {
        if(this.etat==null)
        {
            this.etat=Etat.LIBRE;
        }
    }




}
