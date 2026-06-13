package tn.cityvoice.signalement.controller;

import tn.cityvoice.signalement.dto.ContratReponseRequest;
import tn.cityvoice.signalement.entity.ContratTravail;
import tn.cityvoice.signalement.enums.StatutContrat;
import tn.cityvoice.signalement.service.IContratTravailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/contrats")
@RequiredArgsConstructor
@Slf4j
public class ContratTravailController {

    private final IContratTravailService contratService;

    // ── Contrat par ID ────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ContratTravail getById(@PathVariable Long id) {
        return contratService.getById(id);
    }

    // ── Contrat par numéro (ex: MDN-202505-00042) ─────────────────────
    @GetMapping("/numero/{numero}")
    public ContratTravail getByNumero(@PathVariable String numero) {
        return contratService.getByNumero(numero);
    }

    // ── Contrat actif pour un signalement ─────────────────────────────
    @GetMapping("/signalement/{sigId}")
    public ResponseEntity<ContratTravail> getContratActif(@PathVariable Long sigId) {
        return contratService.getContratActifParSignalement(sigId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    // ── Historique complet des contrats d'un signalement ──────────────
    @GetMapping("/signalement/{sigId}/historique")
    public List<ContratTravail> getHistorique(@PathVariable Long sigId) {
        return contratService.getHistoriqueParSignalement(sigId);
    }

    // ── Tous les contrats en attente (dashboard admin) ─────────────────
    @GetMapping("/en-attente")
    public List<ContratTravail> getEnAttente() {
        return contratService.getContratsEnAttente();
    }

    // ── Tous les contrats (vue admin complète) ─────────────────────────
    @GetMapping
    public List<ContratTravail> getAll() {
        return contratService.getTousLesContrats();
    }

    // ── ACCEPTER un contrat (chef d'équipe signe) ─────────────────────
    @PostMapping("/{id}/accepter")
    public ContratTravail accepter(
        @PathVariable Long id,
        @RequestBody @Valid ContratReponseRequest req,
        @RequestHeader(value = "X-User-Id", defaultValue = "inconnu") String chefId
    ) {
        log.info("[CONTRAT] Acceptation #{} par chef={}", id, chefId);
        return contratService.accepterContrat(id, req, chefId);
    }

    // ── REFUSER un contrat → réaffectation automatique ────────────────
    @PostMapping("/{id}/refuser")
    public ContratTravail refuser(
        @PathVariable Long id,
        @RequestBody @Valid ContratReponseRequest req,
        @RequestHeader(value = "X-User-Id", defaultValue = "inconnu") String chefId
    ) {
        log.info("[CONTRAT] Refus #{} par chef={} motif='{}'", id, chefId, req.getMotifRefus());
        return contratService.refuserContrat(id, req, chefId);
    }

    // ── Contrats du chef connecté (son tableau de bord) ──────────────
    @GetMapping("/chef/{chefId}")
    public List<ContratTravail> getContratsParChef(@PathVariable String chefId) {
        log.info("[CONTRAT] Récupération contrats pour chef={}", chefId);
        return contratService.getContratsParChef(chefId);
    }

    // ── Contrats en attente d'une équipe (routing automatique) ────────
    @GetMapping("/equipe/{equipeCode}/en-attente")
    public List<ContratTravail> getContratsEquipeEnAttente(@PathVariable String equipeCode) {
        return contratService.getContratsEnAttenteParEquipe(equipeCode);
    }

    // ── Tous les contrats d'une équipe (chef sans userId lié) ─────────
    @GetMapping("/equipe/{equipeCode}")
    public List<ContratTravail> getContratsParEquipe(@PathVariable String equipeCode) {
        log.info("[CONTRAT] Tous contrats equipeCode={}", equipeCode);
        return contratService.getContratsParEquipe(equipeCode);
    }

    // ── Stats rapides (admin) ──────────────────────────────────────────
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        List<ContratTravail> tous = contratService.getTousLesContrats();
        long enAttente = tous.stream()
            .filter(c -> c.getStatut() == StatutContrat.EN_ATTENTE_SIGNATURE).count();
        long acceptes  = tous.stream()
            .filter(c -> c.getStatut() == StatutContrat.ACCEPTE).count();
        long refuses   = tous.stream()
            .filter(c -> c.getStatut() == StatutContrat.REFUSE).count();
        return Map.of(
            "total",     tous.size(),
            "enAttente", enAttente,
            "acceptes",  acceptes,
            "refuses",   refuses
        );
    }
}
