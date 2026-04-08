export function fetchWithTimeout(url, ms, options = {}) {
	const ctrl = new AbortController();
	const t = setTimeout(() => ctrl.abort(), ms);
	return fetch(url, { ...options, signal: ctrl.signal }).finally(() => clearTimeout(t));
}

export function errorMessage(err, label, res) {
	if (err.name === 'AbortError') return 'Request timed out. Please try again.';
	if (res) {
		if (res.status === 404) return 'FHIR endpoint not found. Please check if the server is running.';
		if (res.status === 500) return 'Server error. Please try again later.';
	}
	return err.message || `Error loading ${label}`;
}
