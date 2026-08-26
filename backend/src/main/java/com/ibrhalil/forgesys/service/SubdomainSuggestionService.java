package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.SubdomainRules;
import com.ibrhalil.forgesys.dto.SubdomainSuggestionResponse;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates unique subdomain slug candidates from an organization name (K-21).
 * Turkish characters are ASCII-folded before slugification. Algorithm: normalize →
 * slugify → validate pattern → availability; numeric suffixes ({@code -2}, {@code -3},
 * ...) up to {@link #MAX_SUGGESTIONS} candidates.
 */
@Service
@RequiredArgsConstructor
public class SubdomainSuggestionService {

    static final int MAX_SUGGESTIONS = 3;

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
            if (candidate.length() <= SubdomainRules.MAX_LENGTH && isAvailable(candidate)) {
                suggestions.add(candidate);
            }
            suffix++;
        }
        return new SubdomainSuggestionResponse(List.copyOf(suggestions));
    }

    /** Pattern check — keeps the provisioning service self-contained (DTO already constrains). */
    public boolean isValidSubdomain(String subdomain) {
        return subdomain != null && SubdomainRules.PATTERN.matcher(subdomain).matches();
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
