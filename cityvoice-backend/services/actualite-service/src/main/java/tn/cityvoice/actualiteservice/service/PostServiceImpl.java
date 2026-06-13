package tn.cityvoice.actualiteservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.cityvoice.actualiteservice.dto.PostRequestDTO;
import tn.cityvoice.actualiteservice.dto.PostResponseDTO;
import tn.cityvoice.actualiteservice.dto.ShareRequestDTO;
import tn.cityvoice.actualiteservice.entity.Post;
import tn.cityvoice.actualiteservice.entity.PostMedia;
import tn.cityvoice.actualiteservice.entity.enums.TypeMedia;
import tn.cityvoice.actualiteservice.repository.PostRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostRepository        postRepository;
    private final NotificationService   notificationService;
    private final BadWordsService       badWordsService;
    private final ContentModerationService moderationService;

    // ================= CREATE =================
    @Override
    public PostResponseDTO create(PostRequestDTO dto) {
        // 🛡️  CONTRÔLE DE MODÉRATION : on bloque toute publication politique,
        //    haineuse, pornographique, violente ou liée à la drogue AVANT
        //    d'enregistrer quoi que ce soit. CityVoice est une plateforme
        //    citoyenne et doit rester neutre sur ces sujets.
        ContentModerationService.ModerationResult mod =
                moderationService.checkContent(dto.getTitle(), dto.getContent());
        if (mod.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mod.toUserMessage());
        }

        Post post = new Post();

        // Filtrer les mots interdits dans le titre et le contenu
        post.setTitre(badWordsService.filter(dto.getTitle()));
        post.setContenu(badWordsService.filter(dto.getContent()));
        post.setType(dto.getType());
        post.setDatePublication(LocalDateTime.now());

        if (dto.getAuteurId() != null && !dto.getAuteurId().isBlank()) {
            post.setAuteurId(dto.getAuteurId());
        }

        // 🔥 MEDIA
        if (dto.getMediaUrls() != null && !dto.getMediaUrls().isEmpty()) {
            List<PostMedia> medias = dto.getMediaUrls().stream()
                    .map(url -> {
                        PostMedia media = new PostMedia();
                        media.setUrl(url);
                        media.setType(TypeMedia.IMAGE);
                        media.setPost(post);
                        return media;
                    })
                    .collect(Collectors.toList());

            post.setMedias(medias);
        }

        Post saved = postRepository.save(post);

        return mapToResponse(saved);
    }

    // ================= GET ALL =================
    @Override
    @Transactional
    public List<PostResponseDTO> getAll() {
        return postRepository.findAllWithMedias()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ================= GET BY ID =================
    @Override
    @Transactional
    public PostResponseDTO getById(Long id) {
        Post post = postRepository.findByIdWithMedias(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        return mapToResponse(post);
    }

    // ================= UPDATE =================
    @Override
    @Transactional
    public PostResponseDTO update(Long id, PostRequestDTO dto) {

        // 🛡️  Même contrôle de modération lors d'une modification.
        ContentModerationService.ModerationResult mod =
                moderationService.checkContent(dto.getTitle(), dto.getContent());
        if (mod.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mod.toUserMessage());
        }

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        post.setTitre(badWordsService.filter(dto.getTitle()));
        post.setContenu(badWordsService.filter(dto.getContent()));
        post.setType(dto.getType());

        // 🔥 RESET MEDIA (important)
        if (post.getMedias() != null) {
            post.getMedias().clear();
        }

        // 🔥 ADD NEW MEDIA
        if (dto.getMediaUrls() != null && !dto.getMediaUrls().isEmpty()) {
            List<PostMedia> medias = dto.getMediaUrls().stream()
                    .map(url -> {
                        PostMedia media = new PostMedia();
                        media.setUrl(url);
                        media.setType(TypeMedia.IMAGE);
                        media.setPost(post);
                        return media;
                    })
                    .collect(Collectors.toList());

            post.setMedias(medias);
        }

        Post updated = postRepository.save(post);

        return mapToResponse(updated);
    }

    // ================= DELETE =================
    @Override
    public void delete(Long id) {
        postRepository.deleteById(id);
    }

    // ================= SHARE =================
    @Override
    @Transactional
    public PostResponseDTO share(Long originalPostId, ShareRequestDTO req) {
        Post original = postRepository.findByIdWithMedias(originalPostId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        // Incrémenter le compteur du post original
        original.setShareCount(original.getShareCount() + 1);
        postRepository.save(original);

        // Créer le post partagé
        Post shared = new Post();
        shared.setAuteurId(req.getSharerId());
        shared.setContenu(req.getCommentaire() != null ? req.getCommentaire() : "");
        shared.setType(original.getType());
        shared.setTitre(original.getTitre()); // conserver le titre original
        shared.setDatePublication(LocalDateTime.now());
        shared.setSharedFromPostId(original.getId());
        shared.setSharedFromAuteurId(original.getAuteurId());
        // Dénormaliser le contenu + titre + médias du post original pour affichage embarqué
        shared.setSharedFromContent(original.getContenu());
        shared.setSharedFromTitre(original.getTitre());
        if (original.getMedias() != null && !original.getMedias().isEmpty()) {
            List<String> mediaUrls = original.getMedias().stream()
                    .map(PostMedia::getUrl)
                    .collect(Collectors.toList());
            shared.setSharedFromMediaUrls(mediaUrls);
        }

        Post saved = postRepository.save(shared);

        // Notifier l'auteur original — dans try-catch pour ne pas bloquer le partage
        try {
            String nom = req.getSharerNom() != null ? req.getSharerNom() : "Quelqu'un";
            notificationService.send(
                original.getAuteurId(), req.getSharerId(),
                req.getSharerNom(), req.getSharerPhoto(),
                "SHARE",
                nom + " a partagé votre post",
                original.getId()
            );
        } catch (Exception ignored) {}

        return mapToResponse(saved);
    }

    // ================= MAPPING =================
    private PostResponseDTO mapToResponse(Post post) {
        PostResponseDTO dto = new PostResponseDTO();

        dto.setId(post.getId());
        dto.setTitle(post.getTitre());
        dto.setContent(post.getContenu());
        dto.setType(post.getType());
        dto.setAuteurId(post.getAuteurId());
        dto.setCreatedAt(post.getDatePublication());
        dto.setSharedFromPostId(post.getSharedFromPostId());
        dto.setSharedFromAuteurId(post.getSharedFromAuteurId());
        dto.setSharedFromAuteurNom(post.getSharedFromAuteurNom());
        dto.setShareCount(post.getShareCount());

        // Champs dénormalisés du post original
        String fromContent = post.getSharedFromContent();
        String fromTitre   = post.getSharedFromTitre();
        List<String> fromMedia = post.getSharedFromMediaUrls();

        // Anciens posts partagés (avant migration) : récupérer l'original à la volée
        if (post.getSharedFromPostId() != null && fromContent == null) {
            try {
                java.util.Optional<Post> originalOpt = postRepository.findByIdWithMedias(post.getSharedFromPostId());
                if (originalOpt.isPresent()) {
                    Post original = originalOpt.get();
                    fromContent = original.getContenu();
                    fromTitre   = original.getTitre();
                    if (original.getMedias() != null && !original.getMedias().isEmpty()) {
                        fromMedia = original.getMedias().stream()
                                .map(PostMedia::getUrl)
                                .collect(Collectors.toList());
                    }
                }
            } catch (Exception ignored) {}
        }

        dto.setSharedFromContent(fromContent);
        dto.setSharedFromTitre(fromTitre);
        dto.setSharedFromMediaUrls(fromMedia);

        // 🔥 MEDIA → DTO (médias propres du post partageur, généralement vide)
        if (post.getMedias() != null && !post.getMedias().isEmpty()) {
            dto.setMediaUrls(
                    post.getMedias().stream()
                            .map(PostMedia::getUrl)
                            .collect(Collectors.toList())
            );
        }

        return dto;
    }
}