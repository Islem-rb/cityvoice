// config/UserActivityInterceptor.java

package tn.cityvoice.userservice.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class UserActivityInterceptor implements HandlerInterceptor {

    @Autowired
    private UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            String email = auth.getName();
            Optional<User> userOpt = userRepository.findByEmail(email);

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setLastSeenAt(LocalDateTime.now());
                user.setOnline(true);
                userRepository.save(user);
            }
        }

        return true;
    }
}