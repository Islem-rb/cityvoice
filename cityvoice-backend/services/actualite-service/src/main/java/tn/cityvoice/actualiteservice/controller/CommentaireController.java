package tn.cityvoice.actualiteservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.actualiteservice.config.JwtUtil;
import tn.cityvoice.actualiteservice.dto.CommentaireRequestDTO;
import tn.cityvoice.actualiteservice.dto.CommentaireResponseDTO;
import tn.cityvoice.actualiteservice.service.CommentaireService;

import java.util.List;

@RestController
@RequestMapping("/api/posts/{postId}/commentaires")
@RequiredArgsConstructor
public class CommentaireController {

    private final CommentaireService commentaireService;


    @PostMapping
    public CommentaireResponseDTO create(
            @PathVariable("postId") Long postId,
            @RequestBody CommentaireRequestDTO dto,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        dto.setPostId(postId);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String email = JwtUtil.getEmail(token);
            dto.setAuteurEmail(email);
        }

        return commentaireService.create(dto);
    }


    @GetMapping
    public List<CommentaireResponseDTO> getAll(@PathVariable("postId") Long postId) {
        return commentaireService.getByPostId(postId);
    }


    @GetMapping("/{id}")
    public CommentaireResponseDTO getById(@PathVariable("postId") Long postId,
                                          @PathVariable("id") Long id) {
        return commentaireService.getById(id);
    }


    @PutMapping("/{id}")
    public CommentaireResponseDTO update(@PathVariable("postId") Long postId,
                                         @PathVariable("id") Long id,
                                         @RequestBody CommentaireRequestDTO dto) {
        dto.setPostId(postId);
        return commentaireService.update(id, dto);
    }


    @DeleteMapping("/{id}")
    public void delete(@PathVariable("postId") Long postId,
                       @PathVariable("id") Long id) {
        commentaireService.delete(id);
    }
}