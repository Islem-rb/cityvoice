package tn.cityvoice.evenementservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.cityvoice.evenementservice.dto.request.SponsorRequest;
import tn.cityvoice.evenementservice.dto.response.SponsorResponse;
import tn.cityvoice.evenementservice.entity.Evenement;
import tn.cityvoice.evenementservice.entity.EvenementSponsor;
import tn.cityvoice.evenementservice.entity.Sponsor;
import tn.cityvoice.evenementservice.repository.EvenementSponsorRepository;
import tn.cityvoice.evenementservice.repository.SponsorRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SponsorService {

    private final SponsorRepository sponsorRepository;
    private final EvenementSponsorRepository evenementSponsorRepository;
    private final EvenementService evenementService;

    // Créer un sponsor
    public SponsorResponse creerSponsor(SponsorRequest req) {
        Sponsor sponsor = Sponsor.builder()
                .nomEntreprise(req.getNomEntreprise())
                .logoUrl(req.getLogoUrl())
                .siteWeb(req.getSiteWeb())
                .secteurActivite(req.getSecteurActivite())
                .tailleEntreprise(req.getTailleEntreprise())
                .zoneGeographique(req.getZoneGeographique())
                .actifSponsoring(req.getActifSponsoring() != null ? req.getActifSponsoring() : true)
                .build();
        Sponsor saved = sponsorRepository.save(sponsor);
        log.info("Sponsor créé : {}", saved.getNomEntreprise());
        return toResponse(saved);
    }

    // Lister tous les sponsors
    @Transactional(readOnly = true)
    public List<SponsorResponse> listerTous() {
        return sponsorRepository.findAll()
                .stream().map(this::toResponse).toList();
    }

    // Modifier un sponsor
    public SponsorResponse modifierSponsor(Long id, SponsorRequest req) {
        Sponsor s = findById(id);
        s.setNomEntreprise(req.getNomEntreprise());
        s.setLogoUrl(req.getLogoUrl());
        s.setSiteWeb(req.getSiteWeb());
        s.setSecteurActivite(req.getSecteurActivite());
        s.setTailleEntreprise(req.getTailleEntreprise());
        s.setZoneGeographique(req.getZoneGeographique());
        s.setActifSponsoring(req.getActifSponsoring() != null ? req.getActifSponsoring() : true);
        log.info("Sponsor modifié : {}", id);
        return toResponse(sponsorRepository.save(s));
    }

    // Supprimer un sponsor
    public void supprimerSponsor(Long id) {
        sponsorRepository.deleteById(id);
        log.info("Sponsor supprimé : {}", id);
    }

    // Associer sponsor à un événement
    public SponsorResponse associerAEvenement(Long sponsorId, Long evenementId,
                                              String niveau, BigDecimal montant) {
        Sponsor sponsor   = findById(sponsorId);
        Evenement evenement = evenementService.findById(evenementId);

        EvenementSponsor es = EvenementSponsor.builder()
                .sponsor(sponsor)
                .evenement(evenement)
                .niveauSponsorat(niveau)
                .montantSponsorat(montant)
                .build();

        evenementSponsorRepository.save(es);
        log.info("Sponsor {} associé à l'événement {}", sponsorId, evenementId);
        return toResponse(sponsor);
    }

    // Dissocier sponsor d'un événement
    public void dissocierDEvenement(Long sponsorId, Long evenementId) {
        evenementSponsorRepository.deleteByEvenementIdAndSponsorId(evenementId, sponsorId);
        log.info("Sponsor {} dissocié de l'événement {}", sponsorId, evenementId);
    }

    // Lister sponsors d'un événement
    @Transactional(readOnly = true)
    public List<SponsorResponse> listerParEvenement(Long evenementId) {
        return evenementSponsorRepository.findByEvenementId(evenementId)
                .stream()
                .map(this::toResponseAvecNiveau)
                .toList();
    }

    // Utilitaires
    private Sponsor findById(Long id) {
        return sponsorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sponsor introuvable : " + id));
    }

    private SponsorResponse toResponse(Sponsor s) {
        return SponsorResponse.builder()
                .id(s.getId())
                .nomEntreprise(s.getNomEntreprise())
                .logoUrl(s.getLogoUrl())
                .siteWeb(s.getSiteWeb())
                .secteurActivite(s.getSecteurActivite())
                .tailleEntreprise(s.getTailleEntreprise())
                .zoneGeographique(s.getZoneGeographique())
                .actifSponsoring(s.getActifSponsoring())
                .evenementIds(
                        evenementSponsorRepository.findBySponsorId(s.getId())
                                .stream()
                                .map(es -> es.getEvenement().getId())
                                .toList()
                )
                .build();
    }

    private SponsorResponse toResponseAvecNiveau(EvenementSponsor es) {
        return SponsorResponse.builder()
                .id(es.getSponsor().getId())
                .nomEntreprise(es.getSponsor().getNomEntreprise())
                .logoUrl(es.getSponsor().getLogoUrl())
                .siteWeb(es.getSponsor().getSiteWeb())
                .niveauSponsorat(es.getNiveauSponsorat())
                .montantSponsorat(es.getMontantSponsorat())
                .build();
    }
}