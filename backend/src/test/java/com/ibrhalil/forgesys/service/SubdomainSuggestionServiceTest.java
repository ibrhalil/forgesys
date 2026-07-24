package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.SubdomainSuggestionResponse;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubdomainSuggestionServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private SubdomainSuggestionService service;

    @Test
    void slugify_foldsTurkishCharsAndLowercases() {
        assertThat(service.slugify("Gebze Elektrikli Yerli Araba Geliştirme Klübü"))
                .isEqualTo("gebze-elektrikli-yerli-araba-gelistirme-klubu");
    }

    @Test
    void slugify_collapsesMultipleSeparatorsAndTrims() {
        assertThat(service.slugify("  AC--ME   Corp!!  ")).isEqualTo("ac-me-corp");
    }

    @Test
    void slugify_emptyOrNull_returnsEmpty() {
        assertThat(service.slugify("")).isEmpty();
        assertThat(service.slugify(null)).isEmpty();
        assertThat(service.slugify("!!!")).isEmpty();
    }

    @Test
    void isValidSubdomain_acceptsPattern() {
        assertThat(service.isValidSubdomain("acme")).isTrue();
        assertThat(service.isValidSubdomain("ac-me-99")).isTrue();
    }

    @Test
    void isValidSubdomain_rejectsBadPattern() {
        assertThat(service.isValidSubdomain("-acme")).isFalse();
        assertThat(service.isValidSubdomain("acme-")).isFalse();
        assertThat(service.isValidSubdomain("ACME")).isFalse();
        assertThat(service.isValidSubdomain("")).isFalse();
        assertThat(service.isValidSubdomain(null)).isFalse();
    }

    @Test
    void suggest_primaryAvailable_returnsItFirstPlusAlternatives() {
        // Every slug is free — the service returns up to MAX_SUGGESTIONS with the primary first.
        when(companyRepository.findBySubdomain(anyString())).thenReturn(Optional.empty());

        SubdomainSuggestionResponse response = service.suggest("Acme");

        assertThat(response.suggestions()).hasSize(3);
        assertThat(response.suggestions().get(0)).isEqualTo("acme");
        assertThat(response.suggestions()).contains("acme-2", "acme-3");
    }

    @Test
    void suggest_takenPrimary_appendsNumericSuffixes() {
        // Primary taken → suffixed alternatives are returned instead.
        when(companyRepository.findBySubdomain("acme"))
                .thenReturn(Optional.of(new com.ibrhalil.forgesys.entity.Company()));
        when(companyRepository.findBySubdomain("acme-2")).thenReturn(Optional.empty());
        when(companyRepository.findBySubdomain("acme-3")).thenReturn(Optional.empty());
        when(companyRepository.findBySubdomain("acme-4")).thenReturn(Optional.empty());

        SubdomainSuggestionResponse response = service.suggest("Acme");

        assertThat(response.suggestions()).containsExactly("acme-2", "acme-3", "acme-4");
    }

    @Test
    void suggest_neverExceedsMaxThree() {
        when(companyRepository.findBySubdomain(anyString())).thenReturn(Optional.empty());

        SubdomainSuggestionResponse response = service.suggest("Acme");

        assertThat(response.suggestions()).hasSizeLessThanOrEqualTo(3);
    }
}
