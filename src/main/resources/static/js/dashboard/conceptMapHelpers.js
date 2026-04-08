import { CONCEPT_MAP_STATUSES } from './constants.js';

export function normalizeConceptMapStatus(val) {
	const s = val == null ? '' : String(val).trim();
	return CONCEPT_MAP_STATUSES.includes(s) ? s : 'draft';
}

export function slugifyConceptMapName(raw) {
	if (raw == null) return '';
	let s = String(raw).normalize('NFD').replace(/\p{M}/gu, '');
	s = s.toLowerCase();
	s = s.replace(/\s+/g, '-');
	s = s.replace(/[^a-z0-9_-]+/g, '');
	s = s.replace(/-+/g, '-').replace(/^-+|-+$/g, '');
	s = s.replace(/_+/g, '_');
	return s;
}
