import { AJAX_TIMEOUT_MS, SYNDICATION_TIMEOUT_MS } from './constants.js';
import { fetchWithTimeout } from './http.js';

export const dashboardCapability = {
	async loadCapabilityStatement() {
		try {
			const res = await fetchWithTimeout(this.fhirBaseUrl + '/metadata', AJAX_TIMEOUT_MS);
			if (res.ok) {
				this.capabilityStatement = await res.json();
			}
		} catch (err) {
			// Silently fail - assume all operations supported if metadata unavailable
		} finally {
			await this.checkSyndicationAvailability();
		}
	},

	async checkSyndicationAvailability() {
		try {
			const res = await fetchWithTimeout('/syndication/snomed-editions', SYNDICATION_TIMEOUT_MS, {
				method: 'OPTIONS'
			});
			this.syndicationAvailable = res.ok;
		} catch (err) {
			this.syndicationAvailable = false;
		}
		if (!this.syndicationAvailable && this.section === 'syndication') {
			this.section = 'resources';
			this.tab = 'codesystem';
			this.setHash();
			this.loadTabIfNeeded();
		}
	}
};
