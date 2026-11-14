package dev.kevin.gallbladder;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<Settings, Long> {

    /** The saved settings, or empty ones if nothing has been saved yet. */
    default Settings current() {
        return findById(Settings.ID).orElseGet(Settings::new);
    }
}
