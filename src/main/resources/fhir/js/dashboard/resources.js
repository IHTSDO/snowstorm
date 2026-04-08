import { AJAX_TIMEOUT_MS } from './constants.js';
import { fetchWithTimeout, errorMessage } from './http.js';
import { normalizeRow, dedupeCodeSystemsByUrlVersion } from './resourceTransforms.js';

export const dashboardResources = {
	async loadCodeSystems() {
		this.loadingCodesystems = true;
		this.errorCodesystems = null;
		this.codeSystemWarnings = [];
		let res;
		try {
			res = await fetchWithTimeout(this.fhirBaseUrl + '/CodeSystem', AJAX_TIMEOUT_MS);
			const data = await res.json();
			if (!res.ok) throw new Error(data.message || 'Failed to load CodeSystems');
			if (data.entry && data.entry.length > 0) {
				const rows = data.entry.map(e => normalizeRow(e.resource));
				const deduped = dedupeCodeSystemsByUrlVersion(rows);
				this.codeSystems = deduped.kept;
				this.codeSystemWarnings = deduped.warnings;
			} else {
				this.codeSystems = [];
			}
		} catch (err) {
			this.errorCodesystems = errorMessage(err, 'CodeSystems', res);
			this.codeSystems = [];
		} finally {
			this.loadingCodesystems = false;
		}
	},

	async loadValueSets() {
		this.loadingValueSets = true;
		this.errorValueSets = null;
		let res;
		try {
			res = await fetchWithTimeout(this.fhirBaseUrl + '/ValueSet', AJAX_TIMEOUT_MS);
			const data = await res.json();
			if (!res.ok) throw new Error(data.message || 'Failed to load ValueSets');
			if (data.entry && data.entry.length > 0) {
				this.valueSets = data.entry.map(e => normalizeRow(e.resource));
			} else {
				this.valueSets = [];
			}
		} catch (err) {
			this.errorValueSets = errorMessage(err, 'ValueSets', res);
			this.valueSets = [];
		} finally {
			this.loadingValueSets = false;
		}
	},

	onValueSetFileSelected(event) {
		this.addValueSetError = null;
		const file = event.target.files && event.target.files[0];
		if (!file) return;
		const reader = new FileReader();
		reader.onload = () => {
			this.addValueSetJson = reader.result || '';
		};
		reader.onerror = () => {
			this.addValueSetError = 'Failed to read file';
		};
		reader.readAsText(file);
		event.target.value = '';
	},

	async submitAddValueSet() {
		const jsonStr = this.addValueSetJson.trim();
		if (!jsonStr) return;
		this.addValueSetError = null;
		this.addValueSetSaving = true;
		let payload;
		try {
			payload = JSON.parse(jsonStr);
		} catch (e) {
			this.addValueSetError = 'Invalid JSON: ' + (e.message || 'parse error');
			this.addValueSetSaving = false;
			return;
		}
		if (payload.resourceType !== 'ValueSet') {
			this.addValueSetError = 'Resource must be a ValueSet (resourceType: "ValueSet")';
			this.addValueSetSaving = false;
			return;
		}
		try {
			const res = await fetchWithTimeout(this.fhirBaseUrl + '/ValueSet', AJAX_TIMEOUT_MS, {
				method: 'POST',
				headers: { 'Content-Type': 'application/fhir+json' },
				body: JSON.stringify(payload)
			});
			const data = res.ok ? await res.json().catch(() => ({})) : await res.json().catch(() => ({}));
			if (!res.ok) {
				const msg = data.issue && data.issue[0] && (data.issue[0].diagnostics || data.issue[0].details && data.issue[0].details.text);
				throw new Error(msg || data.message || 'Failed to add ValueSet');
			}
			this.clearAddValueSetForm();
			this.showAddValueSetForm = false;
			await this.loadValueSets();
			alert('ValueSet added successfully.');
		} catch (err) {
			this.addValueSetError = err.message || 'Failed to add ValueSet';
		} finally {
			this.addValueSetSaving = false;
		}
	},

	clearAddValueSetForm() {
		this.addValueSetJson = '';
		this.addValueSetError = null;
	},

	async loadConceptMaps() {
		this.loadingConceptMaps = true;
		this.errorConceptMaps = null;
		let res;
		try {
			res = await fetchWithTimeout(this.fhirBaseUrl + '/ConceptMap', AJAX_TIMEOUT_MS);
			const data = await res.json();
			if (!res.ok) throw new Error(data.message || 'Failed to load ConceptMaps');
			if (data.entry && data.entry.length > 0) {
				this.conceptMaps = data.entry.map(e => normalizeRow(e.resource));
			} else {
				this.conceptMaps = [];
			}
		} catch (err) {
			this.errorConceptMaps = errorMessage(err, 'ConceptMaps', res);
			this.conceptMaps = [];
		} finally {
			this.loadingConceptMaps = false;
		}
	}
};
