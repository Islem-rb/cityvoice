package tn.cityvoice.personnelservice.service;

import tn.cityvoice.personnelservice.entity.CandidatureEquipe;

import java.util.List;
import java.util.UUID;

public interface ICandidatureEquipe {
   CandidatureEquipe getCandidatureEquipeBystatut(String statut);
   List<CandidatureEquipe> getAllCandidatureEquipes();
   CandidatureEquipe getCandidatureEquipe(UUID id);

   CandidatureEquipe addCandidatureEquipe(CandidatureEquipe candidatureEquipe);
   void deleteCandidatureEquipe(UUID id_candidatureEquipe);
   void updateCandidatureEquipe(UUID id_candidatureEquipe,CandidatureEquipe candidatureEquipe);



}
