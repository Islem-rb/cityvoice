package tn.cityvoice.personnelservice.service;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import tn.cityvoice.personnelservice.entity.*;
import tn.cityvoice.personnelservice.repository.CvUserRepository;
import tn.cityvoice.personnelservice.repository.EquipeRepository;
import tn.cityvoice.personnelservice.repository.MembreRepository;
import tn.cityvoice.personnelservice.repository.CandidatureEquipeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor

public class IEquipeImp implements IEquipe {
    private EquipeRepository equipeRepo;
    private MembreRepository membreRepo;
    private final CandidatureEquipeRepository candidatureEquipeRepo;  // ← CORRIGÉ
    private final CvUserRepository cvUserRepo;

    @Override
    public Equipe getEquipeByNom(String nom)
    {
        return equipeRepo.findByName(nom);
    }
    @Override
    public List<Equipe> getAllEquipes()
    {
        return equipeRepo.findAll();
    }
    @Override
    public Equipe getEquipeById(UUID id)
    {
        return equipeRepo.findById(id).orElseThrow(()->new RuntimeException(" Equipe Not Found"));

    }
    @Override
    public List<Equipe> getEquipesBySpecialite(String specialite)
    {
        return equipeRepo.findBySpecialite(specialite);
    }
    // EquipeImp.java / EquipeService.java
    public Equipe addEquipe(Equipe equipe) {
        return equipeRepo.save(equipe); // retourner l'entité sauvegardée
    }

    @Override
    @Transactional
    public void deleteEquipe(UUID id) {
        // 1. D'abord, supprimer les candidatures associées
        List<CandidatureEquipe> candidatures =candidatureEquipeRepo.findByEquipeId(id);
        if (candidatures != null && !candidatures.isEmpty()) {
            candidatureEquipeRepo.deleteAll(candidatures);
        }

        // 2. Ensuite supprimer les membres
        Equipe equipe = equipeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Équipe non trouvée"));
        if (equipe.getMembresEquipe() != null && !equipe.getMembresEquipe().isEmpty()) {
            membreRepo.deleteAll(equipe.getMembresEquipe());
        }

        // 3. Enfin supprimer l'équipe
        equipeRepo.delete(equipe);
    }
    @Override
    public void updateEquipe(UUID id,Equipe equipe)
    {
        Equipe Existing=equipeRepo.findById(id).orElseThrow(()->new RuntimeException(" Equipe Not Found"));
        if(equipe.getSpecialite()!=null) Existing.setSpecialite(equipe.getSpecialite());
        if(equipe.getName()!=null) Existing.setName(equipe.getName());
        if(equipe.getGouvernorat()!=null) Existing.setGouvernorat(equipe.getGouvernorat());
        if(equipe.getEtat()!=null) Existing.setEtat(equipe.getEtat());
        equipeRepo.save(Existing);
    }
    @Override
    public void updateEquipeStatus(UUID id, Etat status)
    {
        Equipe EquipeExistante=equipeRepo.findById(id).orElseThrow(()->new RuntimeException("Equipe Not Found"));
        EquipeExistante.setEtat(status);
    }

    @Transactional
    public void addMembreAEquipe(UUID id, MembreEquipe membreEquipe)
    {
        Equipe equipeExistante=equipeRepo.findById(id).orElseThrow(()->new RuntimeException(" Equipe Not Found"));
        if (equipeExistante.getMembresEquipe() == null) {
            equipeExistante.setMembresEquipe(new ArrayList<>());
        }

        // Empêcher un user d'être recruté plusieurs fois (même équipe ou autre)
        if (membreEquipe.getUserId() != null && membreRepo.existsByUserId(membreEquipe.getUserId())) {
            throw new IllegalStateException("Utilisateur déjà membre d'une équipe");
        }
        if (membreEquipe.getEmail() != null && !membreEquipe.getEmail().isBlank()
                && membreRepo.existsByEmailIgnoreCase(membreEquipe.getEmail())) {
            throw new IllegalStateException("Email déjà associé à un membre d'équipe");
        }

        // ajouter côté equipe
        equipeExistante.getMembresEquipe().add(membreEquipe);

        membreRepo.save(membreEquipe);
        equipeRepo.save(equipeExistante);


    }

    @Transactional
    public void removeMembreFromEquipe(UUID equipeId, UUID membreId) {
        Equipe equipe = equipeRepo.findById(equipeId)
                .orElseThrow(() -> new RuntimeException("Equipe Not Found"));

        if (equipe.getMembresEquipe() != null) {
            equipe.getMembresEquipe().removeIf(m -> membreId.equals(m.getId()));
        }
        equipeRepo.save(equipe);

        // Supprimer l'entité membre ensuite
        membreRepo.deleteById(membreId);
    }
    public boolean hasChefEquipe(Equipe equipe) {
        if(equipe.getMembresEquipe().size()==0) return false;
        return equipe.getMembresEquipe().stream().anyMatch(m -> m.getFonction()== Fonction.CHEF_EQUIPE);

    }

    public boolean hasTechnicien(Equipe equipe) {
        return equipe.getMembresEquipe() != null &&
                equipe.getMembresEquipe().stream()
                        .anyMatch(m -> m.getFonction() == Fonction.TECHNICIEN);
    }

    public boolean hasResponsableSecurite(Equipe equipe) {
        return equipe.getMembresEquipe() != null &&
                equipe.getMembresEquipe().stream()
                        .anyMatch(m -> m.getFonction() == Fonction.RESPONSABLE_SECURITE);
    }
    public boolean hasFonction(Equipe equipe, Fonction fonction) {
        if (equipe.getMembresEquipe() == null) return false;

        return equipe.getMembresEquipe().stream()
                .anyMatch(m -> m.getFonction() == fonction);
    }
    @Transactional
    public Equipe createEquipeWithMembre(Equipe equipe, MembreEquipe membre) {

        // initialiser liste si null
        if (equipe.getMembresEquipe() == null) {
            equipe.setMembresEquipe(new ArrayList<>());
        }

        // 🔥 lier les deux côtés

        equipe.getMembresEquipe().add(membre);

        // sauvegarde automatique grâce au cascade
        return equipeRepo.save(equipe);
    }




}
