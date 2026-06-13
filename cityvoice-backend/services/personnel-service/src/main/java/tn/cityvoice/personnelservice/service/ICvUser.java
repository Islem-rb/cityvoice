package tn.cityvoice.personnelservice.service;

import org.springframework.web.multipart.MultipartFile;
import tn.cityvoice.personnelservice.entity.CvUser;

import java.util.List;
import java.util.UUID;

public interface ICvUser {
    CvUser addCV(UUID candidatureId,UUID iduser, MultipartFile file) throws Exception;

    CvUser updateCV(UUID cvId, MultipartFile file) throws Exception;

    void deleteCV(UUID cvId);

    CvUser getCV(UUID cvId);
    List<CvUser> getAllCVsByCandidature(UUID candidatureId);

}
