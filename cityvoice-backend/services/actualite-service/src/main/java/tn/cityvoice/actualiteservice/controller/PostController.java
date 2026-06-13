package tn.cityvoice.actualiteservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.cityvoice.actualiteservice.config.JwtUtil;
import tn.cityvoice.actualiteservice.dto.PostRequestDTO;
import tn.cityvoice.actualiteservice.dto.PostResponseDTO;
import tn.cityvoice.actualiteservice.dto.ShareRequestDTO;
import tn.cityvoice.actualiteservice.service.FileUploadService;
import tn.cityvoice.actualiteservice.service.PostService;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final FileUploadService fileUploadService;


    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public PostResponseDTO createJson(
            @RequestBody PostRequestDTO dto,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            dto.setAuteurEmail(JwtUtil.getEmail(authHeader.substring(7)));
        }
        return postService.create(dto);
    }


    @PostMapping(value = "/with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) //envoyer JSON + fichier
    public PostResponseDTO createWithImage(
            @RequestPart("post") String postJson, //le client envoie le post en texte JSON
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        PostRequestDTO dto;
        try {
            dto = new ObjectMapper().readValue(postJson, PostRequestDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("JSON invalide", e);
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            dto.setAuteurEmail(JwtUtil.getEmail(authHeader.substring(7)));
        }

        if (file != null && !file.isEmpty()) {
            try {
                String url = fileUploadService.saveFile(file); //retourne URL de l’image
                dto.setMediaUrls(List.of(url)); //ajouter l’image au post
            } catch (Exception e) {
                throw new RuntimeException("Erreur upload image", e);
            }
        }

        return postService.create(dto);
    }


    @GetMapping
    public List<PostResponseDTO> getAll() {
        return postService.getAll();
    }


    @GetMapping("/{id}")
    public PostResponseDTO getById(@PathVariable("id") Long id) {
        return postService.getById(id);
    }


    @PutMapping("/{id}")
 public PostResponseDTO update(@PathVariable("id") Long id,
                                 @RequestBody PostRequestDTO dto) {
        return postService.update(id, dto);
    }


    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
       postService.delete(id);
    }

    /** Partager un post — crée un nouveau post lié à l'original */
    @PostMapping("/{id}/share")
    public PostResponseDTO share(
            @PathVariable("id") Long id,
            @RequestBody ShareRequestDTO req) {
        return postService.share(id, req);
    }
}