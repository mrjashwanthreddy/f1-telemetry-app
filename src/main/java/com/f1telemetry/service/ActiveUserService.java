package com.f1telemetry.service;

import com.f1telemetry.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ActiveUserService {

    private final AtomicReference<User> activeUser = new AtomicReference<>(null);

    public void setActiveUser(User user) {
        User previousUser = activeUser.getAndSet(user);
        if (previousUser == null || !previousUser.getUsername().equals(user.getUsername())) {
            log.info("Active player changed: '{}' → '{}'",
                    previousUser != null ? previousUser.getUsername() : "none",
                    user.getUsername());
        }
    }

    public User getActiveUser() {
        return activeUser.get();
    }
}
