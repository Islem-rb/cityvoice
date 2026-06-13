package tn.cityvoice.actualiteservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.cityvoice.actualiteservice.dto.CommentaireRequestDTO;
import tn.cityvoice.actualiteservice.dto.CommentaireResponseDTO;
import tn.cityvoice.actualiteservice.entity.Commentaire;
import tn.cityvoice.actualiteservice.entity.Post;
import tn.cityvoice.actualiteservice.repository.CommentaireRepository;
import tn.cityvoice.actualiteservice.repository.PostRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentaireServiceImpl implements CommentaireService {

    private final CommentaireRepository commentaireRepository;
    private final PostRepository        postRepository;
    private final NotificationService   notificationService;
    private final BadWordsService       badWordsService;

    @Override
    public CommentaireResponseDTO create(CommentaireRequestDTO dto) {
        Post post = postRepository.findById(dto.getPostId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        // Filtrer les mots interdits avant sauvegarde
        String contenuFiltre = badWordsService.filter(dto.getContenu());

        Commentaire c = new Commentaire();
        c.setContenu(contenuFiltre);
        c.setAuteurId(dto.getAuteurId());
        c.setDate(LocalDateTime.now());
        c.setPost(post);

        // Si c'est une réponse à un commentaire existant, lier le parent
        if (dto.getParentId() != null) {
            commentaireRepository.findById(dto.getParentId()).ifPresent(c::setParent);
        }

        Commentaire saved = commentaireRepository.save(c);

        // Notifier l'auteur du post — dans try-catch pour ne pas bloquer la création
        try {
            String nom = (dto.getAuteurNom() != null && !dto.getAuteurNom().isBlank())
                    ? dto.getAuteurNom() : "Quelqu'un";
            String msg = dto.getParentId() != null
                    ? nom + " a répondu à un commentaire sur votre post"
                    : nom + " a commenté votre post";
            notificationService.send(
                post.getAuteurId(), dto.getAuteurId(),
                dto.getAuteurNom(), dto.getAuteurPhoto(),
                "COMMENT", msg, post.getId()
            );
        } catch (Exception ignored) {}

        return mapToResponse(saved);
    }

    @Override
    public List<CommentaireResponseDTO> getByPostId(Long postId) {
        return commentaireRepository.findByPostId(postId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());//transforme chaque commentaire → DTO
    }

    @Override
    public CommentaireResponseDTO getById(Long id) {
        Commentaire c = commentaireRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commentaire not found"));
        return mapToResponse(c); //retour DTO
    }

    @Override
    public CommentaireResponseDTO update(Long id, CommentaireRequestDTO dto) {
        Commentaire c = commentaireRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commentaire not found"));
        c.setContenu(dto.getContenu());
        return mapToResponse(commentaireRepository.save(c));
    }

    @Override
    public void delete(Long id) {
        commentaireRepository.deleteById(id);
    }

    private CommentaireResponseDTO mapToResponse(Commentaire c) {
        CommentaireResponseDTO dto = new CommentaireResponseDTO();
        dto.setId(c.getId());
        dto.setContenu(c.getContenu());
        dto.setDate(c.getDate());
        dto.setAuteurId(c.getAuteurId());
        dto.setPostId(c.getPost().getId());
        // parentId : non-null si c'est une réponse à un commentaire
        if (c.getParent() != null) {
            dto.setParentId(c.getParent().getId());
        }
        return dto;
    }
}