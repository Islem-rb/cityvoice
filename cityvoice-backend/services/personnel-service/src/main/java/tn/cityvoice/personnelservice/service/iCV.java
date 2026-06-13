package tn.cityvoice.personnelservice.service;

import tn.cityvoice.personnelservice.entity.CV;

import java.util.List;
import java.util.UUID;

public interface iCV {
    List<CV> getAllCV();
    void addCV(CV cv);
    void updateCV(UUID id,CV cv);
    void deleteCV(UUID cvId);
}
