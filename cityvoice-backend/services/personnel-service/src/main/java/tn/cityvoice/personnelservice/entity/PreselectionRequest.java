package tn.cityvoice.personnelservice.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PreselectionRequest {

    UUID   userId;
    String poste;          // intitulé du poste (= fonction)
    String equipeNom;
    UUID   chefEquipeId;   // ID du chef d'équipe connecté
}