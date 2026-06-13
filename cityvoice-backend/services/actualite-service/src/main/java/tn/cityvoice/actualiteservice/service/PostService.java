package tn.cityvoice.actualiteservice.service;

import tn.cityvoice.actualiteservice.dto.PostRequestDTO;
import tn.cityvoice.actualiteservice.dto.PostResponseDTO;
import tn.cityvoice.actualiteservice.dto.ShareRequestDTO;

import java.util.List;

public interface PostService {

    PostResponseDTO create(PostRequestDTO dto);

    List<PostResponseDTO> getAll();

    PostResponseDTO getById(Long id);

    PostResponseDTO update(Long id, PostRequestDTO dto);

    void delete(Long id);

    PostResponseDTO share(Long originalPostId, ShareRequestDTO req);
}