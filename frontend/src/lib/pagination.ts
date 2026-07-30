/**
 * Rows-per-page choices for list tables — shared by every page wiring DataTable's
 * footer selector. Backend cap is 1000 (`spring.data.web.pageable.max-page-size`),
 * deliberately above the UI maximum to leave API consumers headroom.
 */
export const PAGE_SIZE_OPTIONS = [5, 10, 50, 100, 200] as const;
