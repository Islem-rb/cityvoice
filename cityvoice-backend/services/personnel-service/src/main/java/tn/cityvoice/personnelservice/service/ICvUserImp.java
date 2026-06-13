package tn.cityvoice.personnelservice.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.cityvoice.personnelservice.entity.CandidatureEquipe;
import tn.cityvoice.personnelservice.entity.CvUser;
import tn.cityvoice.personnelservice.repository.CandidatureEquipeRepository;
import tn.cityvoice.personnelservice.repository.CvUserRepository;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ICvUserImp implements ICvUser {
    private final CvUserRepository cvUserRepository;
    private final CandidatureEquipeRepository candidatureEquipeRepository;
    @Override
    public CvUser addCV(UUID candidatureId,UUID iduser, MultipartFile file) throws Exception {

        CandidatureEquipe candidature  = candidatureEquipeRepository.findById(candidatureId)
                .orElseThrow(() -> new RuntimeException("Candidature not found"));

        if (candidature.getDateExpiration() != null
                && candidature.getDateExpiration().isBefore(java.time.LocalDate.now())) {
            throw new RuntimeException("La candidature est expirée");
        }

        if (cvUserRepository.existsByCandidature_IdAndUserId(candidatureId, iduser)) {
            throw new RuntimeException("Vous avez déjà postulé à cette candidature");
        }

        CvUser cv = new CvUser();
        cv.setFileName(file.getOriginalFilename());
        cv.setFileType(file.getContentType());
        cv.setData(file.getBytes());
        cv.setCandidature(candidature);
        cv.setUserId(iduser);

        return cvUserRepository.save(cv); // ✅ CORRECT

    }
    @Override
    public CvUser updateCV(UUID cvId, MultipartFile file) throws Exception {

        CvUser cv = cvUserRepository.findById(cvId)
                .orElseThrow(() -> new RuntimeException("CV not found"));

        cv.setFileName(file.getOriginalFilename());
        cv.setFileType(file.getContentType());
        cv.setData(file.getBytes());

        return cvUserRepository.save(cv);
    }
    @Override
    public void deleteCV(UUID cvId) {

        CvUser cv = cvUserRepository.findById(cvId)
                .orElseThrow(() -> new RuntimeException("CV not found"));

        cvUserRepository.delete(cv);
    }

    @Override
    public CvUser getCV(UUID cvId) {

        return cvUserRepository.findById(cvId)
                .orElseThrow(() -> new RuntimeException("CV not found"));
    }
    @Override
    public List<CvUser> getAllCVsByCandidature(UUID candidatureId) {

        CandidatureEquipe candidature = candidatureEquipeRepository.findById(candidatureId)
                .orElseThrow(() -> new RuntimeException("Candidature not found"));

        return candidature.getCvs();
    }
    public boolean hasUserApplied(UUID candidatureId, UUID userId) {
        return cvUserRepository.existsByCandidature_IdAndUserId(candidatureId, userId);
    }




}
