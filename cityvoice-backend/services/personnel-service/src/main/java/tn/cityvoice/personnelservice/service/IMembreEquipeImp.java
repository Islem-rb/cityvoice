package tn.cityvoice.personnelservice.service;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import tn.cityvoice.personnelservice.entity.Fonction;
import tn.cityvoice.personnelservice.entity.MembreEquipe;
import tn.cityvoice.personnelservice.repository.MembreRepository;

import java.util.List;
import java.util.UUID;
@Service
@AllArgsConstructor

public class IMembreEquipeImp implements IMembreEquipe {
    private MembreRepository membreRepository;
    @Override
    public MembreEquipe getMembreEquipe(UUID id)
    {
        return membreRepository.findById(id).orElseThrow(()->new RuntimeException("Membre Not Found"));

    }
    @Override
    public List<MembreEquipe> getAllMembreEquipe()
    {
        return membreRepository.findAll();
    }
    @Override
    public void addMembreEquipe(MembreEquipe membre)
    {
        membreRepository.save(membre);
    }
    @Override
    public void deleteMembreEquipe(UUID id)
    {
        membreRepository.deleteById(id);
    }
    @Override
    public void updateMembreEquipe(UUID id,MembreEquipe membreEquipe)
    {
        MembreEquipe existing=membreRepository.findById(id).orElseThrow(()->new RuntimeException("Membre Not Found"));
        if(membreEquipe.getFonction() != null) existing.setFonction(membreEquipe.getFonction());
        if(membreEquipe.getNom() != null) existing.setNom(membreEquipe.getNom());
        if(membreEquipe.getPrenom() != null) existing.setPrenom(membreEquipe.getNom());
        membreRepository.save(existing);

    }
    @Override
    public List<MembreEquipe> getMembreEquipe(Fonction fonction)
    {
        return membreRepository.findMembreEquipesByFonction(fonction);
    }
    @Override
    public List<MembreEquipe> getAllMembreEquipeByNom(String nom)
    {
        return membreRepository.findMembreEquipesByNom(nom);
    }

    @Override
    public void updateFonction(UUID id, Fonction fonction) {
        MembreEquipe Membreexistant=membreRepository.findById(id).orElseThrow(()->new RuntimeException("Membre Not Found"));
        Membreexistant.setFonction(fonction);
    }

}
