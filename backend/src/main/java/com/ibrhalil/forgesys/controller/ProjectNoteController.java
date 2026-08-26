package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.NoteRequest;
import com.ibrhalil.forgesys.dto.NoteResponse;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.service.NoteService;
import com.ibrhalil.forgesys.web.SortGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Notes nested under their NOTES-type container (K-45): unknown container → 404,
 * non-NOTES → 409 {@code project_type_mismatch}. Update/delete stay on the flat
 * {@code /notes/{id}} surface.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/notes")
@RequiredArgsConstructor
public class ProjectNoteController {

    private final NoteService noteService;

    @GetMapping
    @PreAuthorize("hasAuthority('notes:note:read')")
    public ResponseEntity<PageResponse<NoteResponse>> list(
            @PathVariable UUID projectId,
            @PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean pinned) {
        SortGuard.require(pageable, NoteService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(
                noteService.searchInProject(projectId, q, qFields, categoryId, pinned, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('notes:note:write')")
    public ResponseEntity<NoteResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noteService.createInProject(projectId, request));
    }
}
