package tn.cityvoice.userservice.service;

import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.TrustLevel;

public interface TrustService {
    TrustLevel calculate(int points);
    boolean updateIfChanged(User user);
}