package tn.cityvoice.personnelservice.service;

import com.thoughtworks.xstream.mapper.Mapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.cityvoice.personnelservice.entity.CandidatureEquipe;
import tn.cityvoice.personnelservice.entity.CvUser;
import tn.cityvoice.personnelservice.entity.Equipe;
import tn.cityvoice.personnelservice.repository.CandidatureEquipeRepository;
import tn.cityvoice.personnelservice.repository.EquipeRepository;
import tn.cityvoice.personnelservice.repository.QuizzResultRepository;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ICandidatureEquipeImp implements ICandidatureEquipe {
    private CandidatureEquipeRepository candidatureEquipeRepository;
    private EquipeRepository equipeRepository;
    private QuizzResultRepository quizzResultRepository;

    @Override
    public CandidatureEquipe getCandidatureEquipeBystatut(String statut) {
        return candidatureEquipeRepository.findByStatut(statut);

    }
    @Override
    public List<CandidatureEquipe> getAllCandidatureEquipes() {
        return candidatureEquipeRepository.findAll();
    }
    @Override
    public CandidatureEquipe getCandidatureEquipe(UUID id)
    {
        return candidatureEquipeRepository.findById(id).orElseThrow(()-> new RuntimeException("Candidature Equipe Not Found")
        );
    }
    @Override
    public CandidatureEquipe addCandidatureEquipe(CandidatureEquipe candidatureEquipe) {
        return candidatureEquipeRepository.save(candidatureEquipe);
    }
    @Override
    public void deleteCandidatureEquipe(UUID id) {
        CandidatureEquipe candidatureEquipe = candidatureEquipeRepository.findById(id).orElseThrow();
        List<CvUser> cvs = candidatureEquipe.getCvs();
        for (CvUser cv : cvs) {

                quizzResultRepository.deleteByCvId(cv.getId());

        }
        candidatureEquipeRepository.deleteById(id);

    }
    @Override
    public void updateCandidatureEquipe(UUID id, CandidatureEquipe candidatureEquipe) {
        CandidatureEquipe existing = candidatureEquipeRepository.findById(id).orElseThrow(()->new RuntimeException("CandidatureEquipe not found"));
         existing.setNbcandidatsA(candidatureEquipe.getNbcandidatsA());
         if(candidatureEquipe.getStatut() != null) existing.setStatut(candidatureEquipe.getStatut());
         if(candidatureEquipe.getDateExpiration() != null) existing.setDateExpiration(candidatureEquipe.getDateExpiration());
         if(candidatureEquipe.getGouvernorat() != null) existing.setGouvernorat(candidatureEquipe.getGouvernorat());

         candidatureEquipeRepository.save(existing);


    }
    public String getNomEquipe(CandidatureEquipe candidature) {
        if (candidature != null && candidature.getEquipe() != null) {
            return candidature.getEquipe().getName();
        }
        return null; // ou "" selon ton besoin
    }
    @Transactional
    public CandidatureEquipe affecterAEquipe(UUID candidatureId, UUID equipeId) {

        CandidatureEquipe candidature = candidatureEquipeRepository.findById(candidatureId)
                .orElseThrow(() -> new RuntimeException("Candidature not found"));

        Equipe equipe = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new RuntimeException("Equipe not found"));

        candidature.setEquipe(equipe);

        return candidatureEquipeRepository.save(candidature);
    }
    public List<CvUser> getAllCVsByCandidature(UUID candidatureId) {

        CandidatureEquipe candidature = candidatureEquipeRepository.findById(candidatureId)
                .orElseThrow(() -> new RuntimeException("Candidature not found"));

        return candidature.getCvs();
    }
    public CandidatureEquipe addCandidature(CandidatureEquipe c, UUID equipeId) {

        Equipe equipe = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new RuntimeException("Equipe not found"));

        c.setEquipe(equipe);

        return candidatureEquipeRepository.save(c);
    }
    public CandidatureEquipe getByEquipeAndFonction(UUID equipeId, String fonction) {
        return candidatureEquipeRepository
                .findByEquipeIdAndFonction(equipeId, fonction)
                .orElse(null);
    }









}
