import { AJAX_TIMEOUT_MS, CONCEPTMAP_DEFAULT_GROUP_SOURCE, CONCEPTMAP_DEFAULT_URL_PREFIX } from './constants.js';
import { normalizeConceptMapStatus, slugifyConceptMapName } from './conceptMapHelpers.js';
import { fetchWithTimeout } from './http.js';

export const dashboardConceptMapUi = {
	onConceptMapFileSelected(event) {
		this.addConceptMapError = null;
		const file = event.target.files && event.target.files[0];
		if (!file) return;
		const reader = new FileReader();
		reader.onload = () => {
			const text = (reader.result || '').trim();
			let payload;
			try {
				payload = JSON.parse(text);
			} catch (e) {
				this.addConceptMapPayload = null;
				this.addConceptMapUrl = '';
				this.addConceptMapVersion = '';
				this.addConceptMapTitle = '';
				this.addConceptMapName = '';
				this.addConceptMapStatus = 'draft';
				this.addConceptMapDescription = '';
				this.addConceptMapExperimental = false;
				this.addConceptMapGroupSources = [];
				this.addConceptMapError = 'Invalid JSON: ' + (e.message || 'parse error');
				return;
			}
			if (payload.resourceType !== 'ConceptMap') {
				this.addConceptMapPayload = null;
				this.addConceptMapUrl = '';
				this.addConceptMapVersion = '';
				this.addConceptMapTitle = '';
				this.addConceptMapName = '';
				this.addConceptMapStatus = 'draft';
				this.addConceptMapDescription = '';
				this.addConceptMapExperimental = false;
				this.addConceptMapGroupSources = [];
				this.addConceptMapError = 'Resource must be a ConceptMap (resourceType: "ConceptMap")';
				return;
			}
			this.addConceptMapPayload = payload;
			this.addConceptMapUrl = payload.url != null ? String(payload.url) : '';
			this.addConceptMapVersion = payload.version != null ? String(payload.version) : '';
			this.addConceptMapTitle = payload.title != null ? String(payload.title) : '';
			const urlFromFile = (this.addConceptMapUrl || '').trim();
			if (!urlFromFile) {
				this.addConceptMapName = slugifyConceptMapName(this.addConceptMapTitle);
			} else {
				const existingName = payload.name != null ? String(payload.name).trim() : '';
				this.addConceptMapName = existingName || slugifyConceptMapName(this.addConceptMapTitle);
			}
			this.addConceptMapDescription = payload.description != null ? String(payload.description) : '';
			this.addConceptMapStatus = normalizeConceptMapStatus(payload.status);
			this.addConceptMapExperimental = payload.experimental === true;
			if (!(this.addConceptMapUrl || '').trim()) this.syncConceptMapUrlFromName();
			this.syncAddConceptMapGroupSourcesFromPayload();
		};
		reader.onerror = () => {
			this.addConceptMapError = 'Failed to read file';
		};
		reader.readAsText(file);
		event.target.value = '';
	},

	resolvedAddConceptMapName() {
		let n = (this.addConceptMapName || '').trim();
		if (!n) n = slugifyConceptMapName((this.addConceptMapTitle || '').trim());
		return n;
	},

	syncConceptMapUrlFromName() {
		if ((this.addConceptMapUrl || '').trim() !== '') return;
		const name = this.resolvedAddConceptMapName();
		if (!name) return;
		this.addConceptMapUrl = CONCEPTMAP_DEFAULT_URL_PREFIX + name;
	},

	syncConceptMapNameFromTitle() {
		if ((this.addConceptMapName || '').trim() === '') {
			this.addConceptMapName = slugifyConceptMapName(this.addConceptMapTitle || '');
		}
		this.syncConceptMapUrlFromName();
	},

	syncAddConceptMapGroupSourcesFromPayload() {
		const payload = this.addConceptMapPayload;
		if (!payload || !Array.isArray(payload.group)) {
			this.addConceptMapGroupSources = [];
			return;
		}
		this.addConceptMapGroupSources = payload.group.map(g => {
			const raw = g && g.source != null ? String(g.source).trim() : '';
			return raw || CONCEPTMAP_DEFAULT_GROUP_SOURCE;
		});
	},

	async submitAddConceptMap() {
		if (!this.addConceptMapPayload) return;
		const url = (this.addConceptMapUrl || '').trim();
		const version = (this.addConceptMapVersion || '').trim();
		if (!url || !version) {
			this.addConceptMapError = 'URL and version are required.';
			return;
		}
		this.addConceptMapError = null;
		this.addConceptMapSaving = true;
		const payload = JSON.parse(JSON.stringify(this.addConceptMapPayload));
		payload.url = url;
		payload.version = version;
		const title = (this.addConceptMapTitle || '').trim();
		if (title) payload.title = title;
		else delete payload.title;
		const name = this.resolvedAddConceptMapName();
		if (name) payload.name = name;
		else delete payload.name;
		payload.status = this.addConceptMapStatus;
		const desc = (this.addConceptMapDescription || '').trim();
		if (desc) payload.description = desc;
		else delete payload.description;
		payload.experimental = !!this.addConceptMapExperimental;
		const groups = payload.group;
		if (Array.isArray(groups) && groups.length > 0) {
			for (let i = 0; i < groups.length; i++) {
				const src = String(this.addConceptMapGroupSources[i] ?? '').trim();
				if (!src) {
					this.addConceptMapError =
						`Group ${i + 1}: source URI is required (canonical URI of the source code system).`;
					this.addConceptMapSaving = false;
					return;
				}
				groups[i].source = src;
			}
		}
		try {
			const res = await fetchWithTimeout(this.fhirBaseUrl + '/ConceptMap', AJAX_TIMEOUT_MS, {
				method: 'POST',
				headers: { 'Content-Type': 'application/fhir+json' },
				body: JSON.stringify(payload)
			});
			const data = res.ok ? await res.json().catch(() => ({})) : await res.json().catch(() => ({}));
			if (!res.ok) {
				const msg = data.issue && data.issue[0] && (data.issue[0].diagnostics || data.issue[0].details && data.issue[0].details.text);
				throw new Error(msg || data.message || 'Failed to add ConceptMap');
			}
			this.clearAddConceptMapForm();
			this.showAddConceptMapForm = false;
			await this.loadConceptMaps();
			alert('ConceptMap added successfully.');
		} catch (err) {
			this.addConceptMapError = err.message || 'Failed to add ConceptMap';
		} finally {
			this.addConceptMapSaving = false;
		}
	},

	clearAddConceptMapForm() {
		this.addConceptMapPayload = null;
		this.addConceptMapUrl = '';
		this.addConceptMapVersion = '';
		this.addConceptMapTitle = '';
		this.addConceptMapName = '';
		this.addConceptMapStatus = 'draft';
		this.addConceptMapDescription = '';
		this.addConceptMapExperimental = false;
		this.addConceptMapGroupSources = [];
		this.addConceptMapError = null;
	},

	confirmDeleteConceptMap(id, title) {
		this.deleteConfirmId = id;
		this.deleteConfirmTitle = title;
		this.deleteConceptMapError = null;
		bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteConceptMapModal')).show();
	},

	async deleteConceptMap() {
		if (!this.deleteConfirmId) return;
		this.deletingConceptMap = true;
		this.deleteConceptMapError = null;
		let res;
		try {
			res = await fetchWithTimeout(this.fhirBaseUrl + '/ConceptMap/' + this.deleteConfirmId, AJAX_TIMEOUT_MS, {
				method: 'DELETE'
			});
			if (!res.ok) {
				const data = await res.json().catch(() => ({}));
				const msg = data.issue && data.issue[0] && (data.issue[0].diagnostics || data.issue[0].details && data.issue[0].details.text);
				throw new Error(msg || data.message || 'Failed to delete ConceptMap');
			}
			bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteConceptMapModal')).hide();
			await this.loadConceptMaps();
		} catch (err) {
			this.deleteConceptMapError = err.name === 'AbortError' ? 'Request timed out. Please try again.' : (err.message || 'Failed to delete ConceptMap');
		} finally {
			this.deletingConceptMap = false;
		}
	}
};
