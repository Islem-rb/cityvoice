package tn.cityvoice.actualiteservice.service;

import tn.cityvoice.actualiteservice.dto.CommentaireRequestDTO;
import tn.cityvoice.actualiteservice.dto.CommentaireResponseDTO;
import java.util.List;

public interface CommentaireService {
    CommentaireResponseDTO create(CommentaireRequestDTO dto);
    List<CommentaireResponseDTO> getByPostId(Long postId);
    CommentaireResponseDTO getById(Long id);
    CommentaireResponseDTO update(Long id, CommentaireRequestDTO dto);
    void delete(Long id);
}