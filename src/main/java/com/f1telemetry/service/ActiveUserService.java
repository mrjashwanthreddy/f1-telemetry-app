package com.f1telemetry.service;

import com.f1telemetry.domain.SimDriver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ActiveUserService {

    private final AtomicReference<SimDriver> activeDriver = new AtomicReference<>(null);

    public void setActiveUser(SimDriver driver) {
        SimDriver previousDriver = activeDriver.getAndSet(driver);
        if (previousDriver == null || !previousDriver.getUsername().equals(driver.getUsername())) {
            log.info("Active player changed: '{}' → '{}'",
                    previousDriver != null ? previousDriver.getUsername() : "none",
                    driver.getUsername());
        }
    }

    public SimDriver getActiveUser() {
        return activeDriver.get();
    }

    public SimDriver getActiveDriver() {
        return activeDriver.get();
    }
}
