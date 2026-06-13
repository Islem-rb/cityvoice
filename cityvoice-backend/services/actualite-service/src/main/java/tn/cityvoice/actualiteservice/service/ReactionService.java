package tn.cityvoice.actualiteservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.cityvoice.actualiteservice.dto.ReactionCountDTO;
import tn.cityvoice.actualiteservice.dto.ReactionRequestDTO;
import tn.cityvoice.actualiteservice.dto.ReactionSummaryDTO;
import tn.cityvoice.actualiteservice.dto.ReactorDTO;
import tn.cityvoice.actualiteservice.entity.Post;
import tn.cityvoice.actualiteservice.entity.Reaction;
import tn.cityvoice.actualiteservice.entity.enums.TypeReaction;
import tn.cityvoice.actualiteservice.repository.PostRepository;
import tn.cityvoice.actualiteservice.repository.ReactionRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final PostRepository     postRepository;
    private final NotificationService notificationService;

    // ── Ajouter / changer une réaction ──────────────────────
    public ReactionSummaryDTO react(Long postId, ReactionRequestDTO dto) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post non trouvé"));

        Optional<Reaction> existing = reactionRepository
                .findByUserIdAndPost_Id(dto.getUserId(), postId);

        if (existing.isPresent()) {
            Reaction r = existing.get();
            if (r.getType() == dto.getType()) {
                // Même réaction → supprimer (toggle off)
                reactionRepository.delete(r);
            } else {
                // Réaction différente → changer
                r.setType(dto.getType());
                String uName2 = dto.getUserName();
                if (uName2 != null && !uName2.isBlank()) r.setUserName(uName2);
                r.setDate(LocalDateTime.now());
                reactionRepository.save(r);
                // Notifier l'auteur du post (changement de réaction) — dans try-catch pour ne pas bloquer
                try {
                    String emoji = emojiFor(dto.getType());
                    notificationService.send(
                        post.getAuteurId(), dto.getUserId(),
                        dto.getUserName(), dto.getUserPhoto(),
                        "REACTION",
                        (dto.getUserName() != null ? dto.getUserName() : "Quelqu'un")
                            + " a réagi " + emoji + " à votre post",
                        postId
                    );
                } catch (Exception ignored) {}
            }
        } else {
            // Nouvelle réaction
            Reaction r = new Reaction();
            r.setUserId(dto.getUserId());
            // ✅ Fallback si userName vide ou null
            String uName = dto.getUserName();
            r.setUserName((uName != null && !uName.isBlank()) ? uName : "Utilisateur");
            r.setType(dto.getType());
            r.setPost(post);
            r.setDate(LocalDateTime.now());
            reactionRepository.save(r);
            // Notifier l'auteur du post (nouvelle réaction) — dans try-catch pour ne pas bloquer
            try {
                String emoji = emojiFor(dto.getType());
                notificationService.send(
                    post.getAuteurId(), dto.getUserId(),
                    dto.getUserName(), dto.getUserPhoto(),
                    "REACTION",
                    (dto.getUserName() != null ? dto.getUserName() : "Quelqu'un")
                        + " a réagi " + emoji + " à votre post",
                    postId
                );
            } catch (Exception ignored) {}
        }

        return buildSummary(postId, dto.getUserId());
    }

    // ── Supprimer une réaction ───────────────────────────────
    public ReactionSummaryDTO unreact(Long postId, String userId) {
        reactionRepository.findByUserIdAndPost_Id(userId, postId)
                .ifPresent(reactionRepository::delete);
        return buildSummary(postId, userId);
    }

    // ── Construire le résumé ─────────────────────────────────
    public ReactionSummaryDTO buildSummary(Long postId, String userId) {
        List<Reaction> all = reactionRepository.findByPost_Id(postId);

        // Comptage par type
        Map<TypeReaction, Long> countMap = all.stream()
                .collect(Collectors.groupingBy(Reaction::getType, Collectors.counting()));

        List<ReactionCountDTO> counts = countMap.entrySet().stream()
                .map(e -> new ReactionCountDTO(e.getKey().name(), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(ReactionCountDTO::getCount).reversed())
                .collect(Collectors.toList());

        // Réaction de l'utilisateur courant
        String userReaction = null;
        if (userId != null && !userId.isBlank()) {
            userReaction = all.stream()
                    .filter(r -> userId.equals(r.getUserId()))
                    .findFirst()
                    .map(r -> r.getType().name())
                    .orElse(null);
        }

        // Liste des reactors (max 20 pour le tooltip)
        List<ReactorDTO> reactors = all.stream()
                .sorted(Comparator.comparing(Reaction::getDate).reversed())
                .limit(20)
                .map(r -> new ReactorDTO(
                        r.getUserId(),
                        (r.getUserName() != null && !r.getUserName().isBlank()) ? r.getUserName() : "Utilisateur",
                        r.getType().name()))
                .collect(Collectors.toList());

        return new ReactionSummaryDTO(counts, userReaction, all.size(), reactors);
    }

    // ── Helper emoji ─────────────────────────────────────────
    private String emojiFor(TypeReaction type) {
        if (type == null) return "";
        return switch (type) {
            case JAIME   -> "👍";
            case UTILE   -> "💡";
            case BRAVO   -> "👏";
            case SOUTIEN -> "🤝";
        };
    }
}
