package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.PlatformMailInfoResponse;
import com.ibrhalil.forgesys.dto.PlatformMailPreviewRequest;
import com.ibrhalil.forgesys.dto.PlatformMailPreviewResponse;
import com.ibrhalil.forgesys.dto.PlatformMailTestSendRequest;
import com.ibrhalil.forgesys.dto.PlatformMailTestSendResponse;
import com.ibrhalil.forgesys.service.PlatformMailTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** K-51 platform mail testing — platform tokens only (scope == 'platform'). */
@RestController
@RequestMapping("/api/v1/platform/mail")
@RequiredArgsConstructor
public class PlatformMailController {

    private static final String PLATFORM_MAIL_TEST =
            "hasAuthority('platform:mail:test') and authentication.principal.scope == 'platform'";

    private final PlatformMailTestService platformMailTestService;

    @GetMapping("/info")
    @PreAuthorize(PLATFORM_MAIL_TEST)
    public ResponseEntity<PlatformMailInfoResponse> info() {
        return ResponseEntity.ok(platformMailTestService.info());
    }

    @PostMapping("/preview")
    @PreAuthorize(PLATFORM_MAIL_TEST)
    public ResponseEntity<PlatformMailPreviewResponse> preview(@Valid @RequestBody PlatformMailPreviewRequest request) {
        return ResponseEntity.ok(platformMailTestService.preview(request));
    }

    @PostMapping("/test-send")
    @PreAuthorize(PLATFORM_MAIL_TEST)
    public ResponseEntity<PlatformMailTestSendResponse> testSend(@Valid @RequestBody PlatformMailTestSendRequest request) {
        return ResponseEntity.ok(platformMailTestService.testSend(request));
    }
}
