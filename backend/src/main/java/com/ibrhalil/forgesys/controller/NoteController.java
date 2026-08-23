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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Standalone tenant-shared notes (K-44 / Epic 3.2). List supports the global
 * {@code q} search (title + content) plus first-match {@code categoryId}/{@code pinned}
 * filters, translated into filter-engine criteria and AND-combined (the audit-list
 * convention).
 */
@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    @PreAuthorize("hasAuthority('notes:note:read')")
    public ResponseEntity<PageResponse<NoteResponse>> list(
            @PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean pinned) {
        SortGuard.require(pageable, NoteService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(noteService.search(q, categoryId, pinned, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('notes:note:read')")
    public ResponseEntity<NoteResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(noteService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('notes:note:write')")
    public ResponseEntity<NoteResponse> create(@Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noteService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('notes:note:write')")
    public ResponseEntity<NoteResponse> update(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(noteService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('notes:note:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        noteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
