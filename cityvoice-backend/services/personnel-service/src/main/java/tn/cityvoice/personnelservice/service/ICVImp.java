package tn.cityvoice.personnelservice.service;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import tn.cityvoice.personnelservice.entity.CV;
import tn.cityvoice.personnelservice.repository.CVRepository;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor

public class ICVImp  implements iCV{
    private CVRepository cvrepository;

    @Override
    public List<CV> getAllCV()
    {
        return cvrepository.findAll();
    }
    @Override
    public void addCV(CV cv)
    {
        cvrepository.save(cv);
    }
    @Override
    public void updateCV(UUID id, CV cv)
    {
        CV existingCV = cvrepository.findById(id).orElseThrow(()->new RuntimeException("CV not found"));
        if(cv.getCompetences()!=null) existingCV.setCompetences(cv.getCompetences());
        if(cv.getExperience()!=null) existingCV.setExperience(cv.getExperience());
        if(cv.getDiplome()!=null) existingCV.setDiplome(cv.getDiplome());
        cvrepository.save(existingCV);
    }
    @Override
    public void deleteCV(UUID cvId)
    {
        cvrepository.deleteById(cvId);
    }
}
