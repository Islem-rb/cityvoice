package tn.cityvoice.actualiteservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.actualiteservice.dto.ReactionRequestDTO;
import tn.cityvoice.actualiteservice.dto.ReactionSummaryDTO;
import tn.cityvoice.actualiteservice.service.ReactionService;

@RestController
@RequestMapping("/api/reactions")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class ReactionController {

    private final ReactionService reactionService;

    /**
     * GET /api/reactions/post/{postId}?userId={userId}
     * Retourne le résumé des réactions pour un post donné.
     */
    @GetMapping("/post/{postId}")
    public ReactionSummaryDTO getSummary(
            @PathVariable("postId") Long postId,
            @RequestParam(name = "userId", required = false) String userId) {
        return reactionService.buildSummary(postId, userId);
    }

    /**
     * POST /api/reactions/post/{postId}
     * Body: { userId, userName, type }
     * Ajoute ou change la réaction. Si même type → supprime (toggle).
     */
    @PostMapping("/post/{postId}")
    public ReactionSummaryDTO react(
            @PathVariable("postId") Long postId,
            @RequestBody ReactionRequestDTO dto) {
        return reactionService.react(postId, dto);
    }

    /**
     * DELETE /api/reactions/post/{postId}?userId={userId}
     * Supprime la réaction de l'utilisateur.
     */
    @DeleteMapping("/post/{postId}")
    public ReactionSummaryDTO unreact(
            @PathVariable("postId") Long postId,
            @RequestParam("userId") String userId) {
        return reactionService.unreact(postId, userId);
    }
}
