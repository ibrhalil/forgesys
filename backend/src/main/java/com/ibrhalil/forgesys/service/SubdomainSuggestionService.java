package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.SubdomainSuggestionResponse;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Generates unique subdomain slug candidates from an organization name (K-21). Used by
 * the signup form so the user can pick one during tenant creation. Turkish characters
 * are ASCII-folded before slugification.
 *
 * <p>Algorithm: normalize → slugify → validate pattern → confirm availability against
 * active + provisioning tenants. If the primary slug is taken, numeric suffixes
 * ({@code -2}, {@code -3}, ...) are tried up to {@link #MAX_SUGGESTIONS} candidates.
 */
@Service
@RequiredArgsConstructor
public class SubdomainSuggestionService {

    static final int MAX_SUGGESTIONS = 3;
    private static final int MAX_SUBDOMAIN_LENGTH = 100;
    private static final Pattern SUBDOMAIN_PATTERN =
            Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?$");

    private static final Map<Character, Character> TURKISH_ASCII = Map.ofEntries(
            Map.entry('ç', 'c'), Map.entry('Ç', 'c'),
            Map.entry('ğ', 'g'), Map.entry('Ğ', 'g'),
            Map.entry('ı', 'i'), Map.entry('I', 'i'), Map.entry('İ', 'i'),
            Map.entry('ö', 'o'), Map.entry('Ö', 'o'),
            Map.entry('ş', 's'), Map.entry('Ş', 's'),
            Map.entry('ü', 'u'), Map.entry('Ü', 'u')
    );

    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public SubdomainSuggestionResponse suggest(String name) {
        String baseSlug = slugify(name);
        List<String> suggestions = new ArrayList<>();
        if (!baseSlug.isBlank() && isAvailable(baseSlug)) {
            suggestions.add(baseSlug);
        }
        int suffix = 2;
        while (suggestions.size() < MAX_SUGGESTIONS && suffix <= 99) {
            String candidate = baseSlug + "-" + suffix;
            if (candidate.length() <= MAX_SUBDOMAIN_LENGTH && isAvailable(candidate)) {
                suggestions.add(candidate);
            }
            suffix++;
        }
        return new SubdomainSuggestionResponse(List.copyOf(suggestions));
    }

    /**
     * Validates a user-supplied subdomain against the pattern. Used by the provisioning
     * service to reject malformed values early (the DTO pattern already constrains the
     * request, but the runtime check keeps the service self-contained).
     */
    public boolean isValidSubdomain(String subdomain) {
        return subdomain != null && SUBDOMAIN_PATTERN.matcher(subdomain).matches();
    }

    public boolean isAvailable(String subdomain) {
        return isValidSubdomain(subdomain) && companyRepository.findBySubdomain(subdomain).isEmpty();
    }

    String slugify(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(name.length());
        for (char c : name.toCharArray()) {
            sb.append(TURKISH_ASCII.getOrDefault(c, c));
        }
        return sb.toString()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-");
    }
}
