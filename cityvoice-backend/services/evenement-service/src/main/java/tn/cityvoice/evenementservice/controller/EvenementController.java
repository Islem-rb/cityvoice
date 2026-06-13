package tn.cityvoice.evenementservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.evenementservice.dto.request.CreateEvenementRequest;
import tn.cityvoice.evenementservice.dto.request.InscriptionRequest;
import tn.cityvoice.evenementservice.dto.response.EvenementResponse;
import tn.cityvoice.evenementservice.dto.response.ParticipantResponse;
import tn.cityvoice.evenementservice.dto.response.StatsResponse;
import tn.cityvoice.evenementservice.entity.Participant;
import tn.cityvoice.evenementservice.service.EvenementService;

import java.util.List;


@RestController
@RequestMapping("/api/evenements")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class EvenementController {

    private final EvenementService evenementService;

    @PostMapping
    public ResponseEntity<EvenementResponse> creer(
            @Valid @RequestBody CreateEvenementRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(evenementService.creerEvenement(req));
    }

    @GetMapping
    public ResponseEntity<List<EvenementResponse>> lister() {
        return ResponseEntity.ok(evenementService.listerEvenementsPublies());
    }

    @GetMapping("/tous")
    public ResponseEntity<List<EvenementResponse>> listerTous() {
        return ResponseEntity.ok(evenementService.listerTous());
    }
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats() {
        return ResponseEntity.ok(evenementService.getStats());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EvenementResponse> getById(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(evenementService.getById(id));
    }

    @PutMapping("/{id}/publier")
    public ResponseEntity<EvenementResponse> publier(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(evenementService.publierEvenement(id));
    }

    @PutMapping("/{id}/annuler")
    public ResponseEntity<EvenementResponse> annuler(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(evenementService.annulerEvenement(id));
    }
    @PutMapping("/{id}")
    public ResponseEntity<EvenementResponse> modifier(
            @PathVariable("id") Long id,
            @Valid @RequestBody CreateEvenementRequest req) {
        return ResponseEntity.ok(evenementService.modifierEvenement(id, req));
    }

    @PostMapping("/{id}/inscrire")
    public ResponseEntity<ParticipantResponse> inscrire(
            @PathVariable("id") Long id,
            @Valid @RequestBody InscriptionRequest req) {
        Participant p = evenementService.inscrireParticipant(id, req);
        ParticipantResponse response = ParticipantResponse.builder()
                .id(p.getId())
                .citoyenId(p.getCitoyenId())
                .emailCitoyen(p.getEmailCitoyen())
                .nomCitoyen(p.getNomCitoyen())
                .qrToken(p.getQrToken())
                .statutPresence(p.getStatutPresence().name())
                .inscritLe(p.getInscritLe() != null ? p.getInscritLe().toString() : null)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(
            @PathVariable("id") Long id) {
        evenementService.supprimerEvenement(id);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}/participants")
    public ResponseEntity<List<ParticipantResponse>> getParticipants(
            @PathVariable("id") Long id) {
        List<Participant> participants = evenementService.getParticipants(id);
        List<ParticipantResponse> response = participants.stream()
                .map(p -> ParticipantResponse.builder()
                        .id(p.getId())
                        .citoyenId(p.getCitoyenId())
                        .nomCitoyen(p.getNomCitoyen())
                        .emailCitoyen(p.getEmailCitoyen())
                        .qrToken(p.getQrToken())
                        .statutPresence(p.getStatutPresence().name())
                        .inscritLe(p.getInscritLe() != null ? p.getInscritLe().toString() : null)
                        .build())
                .toList();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/participants/{participantId}")
    public ResponseEntity<Void> supprimerParticipant(
            @PathVariable("participantId") Long participantId) {
        evenementService.supprimerParticipant(participantId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/participants/{participantId}/confirmer")
    public ResponseEntity<ParticipantResponse> confirmerPresence(
            @PathVariable("participantId") Long participantId) {
        Participant p = evenementService.confirmerPresence(participantId);
        return ResponseEntity.ok(ParticipantResponse.builder()
                .id(p.getId())
                .nomCitoyen(p.getNomCitoyen())
                .emailCitoyen(p.getEmailCitoyen())
                .statutPresence(p.getStatutPresence().name())
                .build());
    }
}