package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.NoteRequest;
import com.ibrhalil.forgesys.dto.NoteResponse;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.service.NoteService;
import com.ibrhalil.forgesys.web.SortGuard;
import com.ibrhalil.forgesys.web.filter.SearchQuery;
import com.ibrhalil.forgesys.web.filter.SearchRequests;
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

import java.util.List;
import java.util.UUID;

/**
 * Flat cross-container note surface (K-44/K-45): {@code ?projectId=} narrows the
 * list; writes without a {@code projectId} land in the default NOTES container.
 * Container-nested reads/writes live on {@link ProjectNoteController}.
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
            SearchQuery searchQuery,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(required = false) UUID projectId) {
        SortGuard.require(pageable, NoteService.FILTER_FIELDS);
        if (searchQuery.present()) {
            SearchRequest request = searchQuery.request();
            return ResponseEntity.ok(PageResponse.of(noteService.search(request, pageable)));
        }
        return ResponseEntity.ok(PageResponse.of(
                noteService.search(q, qFields, categoryId, pinned, projectId, pageable)));
    }

    /** Filter-engine variant of the list: paging + multi-sort + filters + {@code q} in one POST body. */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('notes:note:read')")
    public ResponseEntity<PageResponse<NoteResponse>> search(@Valid @RequestBody SearchRequest request) {
        Pageable pageable = SearchRequests.toPageable(request, NoteService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(noteService.search(request, pageable)));
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
