package com.f1telemetry.service;

import com.f1telemetry.domain.User;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class ActiveUserService {

    private final AtomicReference<User> activeUser = new AtomicReference<>(null);

    public void setActiveUser(User user) {
        activeUser.set(user);
    }

    public User getActiveUser() {
        return activeUser.get();
    }
}
