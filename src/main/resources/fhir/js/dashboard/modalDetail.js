import { AJAX_TIMEOUT_MS } from './constants.js';
import { fetchWithTimeout } from './http.js';

export const dashboardModalDetail = {
	async viewDetail(type, id) {
		this.modalType = type;
		this.modalLoading = true;
		this.modalDetail = null;
		this.modalError = null;
		const modalEl = document.getElementById(type + 'Modal');
		const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
		modal.show();

		const url = `${this.fhirBaseUrl}/${type === 'codesystem' ? 'CodeSystem' : type === 'valueset' ? 'ValueSet' : 'ConceptMap'}/${id}`;
		let res;
		try {
			res = await fetchWithTimeout(url, AJAX_TIMEOUT_MS);
			const data = await res.json();
			if (!res.ok) throw new Error(data.message || 'Not found');
			this.modalDetail = data;
			if (type === 'conceptmap' && this.modalDetail) {
				const g0 = Array.isArray(this.modalDetail.group) && this.modalDetail.group.length
					? this.modalDetail.group[0]
					: null;
				if (g0) {
					if (g0.source != null && String(g0.source).trim() !== '') {
						this.modalDetail.sourceCodeSystem = g0.source;
					}
					if (g0.target != null && String(g0.target).trim() !== '') {
						this.modalDetail.targetCodeSystem = g0.target;
					}
				}
				if (!this.modalDetail.fullUrl && this.modalDetail.id) {
					this.modalDetail.fullUrl = `${this.fhirBaseUrl}/ConceptMap/${encodeURIComponent(this.modalDetail.id)}`;
				}
			}
		} catch (err) {
			if (err.name === 'AbortError') this.modalError = 'Request timed out. Please try again.';
			else if (typeof res !== 'undefined' && res.status === 404) this.modalError = `${type === 'codesystem' ? 'CodeSystem' : type === 'valueset' ? 'ValueSet' : 'ConceptMap'} not found.`;
			else if (typeof res !== 'undefined' && res.status === 500) this.modalError = 'Server error. Please try again later.';
			else this.modalError = err.message || 'Error loading details';
		} finally {
			this.modalLoading = false;
		}
	},

	async openConceptMapTranslateExample() {
		const fullUrl = this.modalDetail?.fullUrl;
		if (!fullUrl) return;
		this.conceptMapTranslateExampleBusy = true;
		let res;
		try {
			res = await fetchWithTimeout(fullUrl, AJAX_TIMEOUT_MS);
			const data = await res.json();
			if (!res.ok) throw new Error(data.message || 'Failed to load ConceptMap');
			const mapUrl = data.url != null ? String(data.url).trim() : '';
			const g0 = Array.isArray(data.group) && data.group.length ? data.group[0] : null;
			const system = g0 && g0.source != null ? String(g0.source).trim() : '';
			const el0 = g0 && Array.isArray(g0.element) && g0.element.length ? g0.element[0] : null;
			const code = el0 && el0.code != null ? String(el0.code).trim() : '';
			if (!mapUrl || !system || !code) {
				alert(
					'This ConceptMap needs a canonical url, at least one group with source, and at least one element with code to open a translate example.'
				);
				return;
			}
			const qs = new URLSearchParams();
			qs.append('code', code);
			qs.append('system', system);
			qs.append('url', mapUrl);
			const path = `/ConceptMap/$translate?${qs.toString()}`;
			window.open(`${this.fhirBaseUrl}${path}`, '_blank', 'noopener,noreferrer');
		} catch (err) {
			if (err.name === 'AbortError') alert('Request timed out. Please try again.');
			else alert(err.message || 'Failed to open translate example');
		} finally {
			this.conceptMapTranslateExampleBusy = false;
		}
	}
};
