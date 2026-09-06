package com.aierp.platform.web;
import java.time.Instant;
import java.util.Locale;
public final class Checks {
    private Checks() {}
    public static String email(String email) {
        if (email == null || email.length() > 320 || !email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new ValidationFailure("email", "A valid email is required");
        return email.trim().toLowerCase(Locale.ROOT);
    }
    public static void schedule(String title, Instant starts, Instant ends) {
        if (title == null || title.isBlank() || title.length()>300) throw new ValidationFailure("title","Title must contain 1 to 300 characters");
        if (starts == null || ends == null || !ends.isAfter(starts)) throw new ValidationFailure("endsAt","End must be after start");
    }
    public static long version(Long version) {
        if (version == null || version < 0) throw new ValidationFailure("rowVersion","A nonnegative rowVersion is required");
        return version;
    }
}
