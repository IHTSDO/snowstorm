export const dashboardRouting = {
	init() {
		const txParam = new URLSearchParams(window.location.search).get('tx');
		if (txParam) {
			this.fhirBaseUrl = txParam.replace(/\/$/, '');
			this.continueDashboardInit();
		} else {
			this.txUrlDialogError = null;
			this.txUrlPrompt = this.fhirBaseUrl;
			this.$nextTick(() => {
				const el = document.getElementById('txUrlModal');
				if (el && typeof bootstrap !== 'undefined') {
					bootstrap.Modal.getOrCreateInstance(el).show();
				}
			});
		}
	},

	continueDashboardInit() {
		this.loadCapabilityStatement();
		window.addEventListener('hashchange', () => this.initFromHash());
		this.initFromHash();
		this.routingInitialized = true;
	},

	openTxUrlDialog() {
		this.txUrlDialogError = null;
		this.txUrlPrompt = this.fhirBaseUrl;
		this.$nextTick(() => {
			const el = document.getElementById('txUrlModal');
			if (el && typeof bootstrap !== 'undefined') {
				bootstrap.Modal.getOrCreateInstance(el).show();
			}
		});
	},

	refreshAfterTxUrlChange() {
		this.loadCapabilityStatement();
		this.codeSystems = [];
		this.valueSets = [];
		this.conceptMaps = [];
		this.editions = [];
		this.snomedCodeSystems = [];
		this.installState = {};
		this.errorCodesystems = null;
		this.errorValueSets = null;
		this.errorConceptMaps = null;
		this.loadTabIfNeeded();
		this.loadSyndicationIfNeeded();
	},

	confirmTxUrl() {
		const raw = (this.txUrlPrompt || '').trim();
		if (!raw) {
			this.txUrlDialogError = 'Please enter a FHIR Terminology Server URL.';
			return;
		}
		let parsed;
		try {
			parsed = new URL(raw);
		} catch {
			this.txUrlDialogError = 'Please enter a valid URL (e.g. https://example.com/fhir).';
			return;
		}
		if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
			this.txUrlDialogError = 'URL must use http or https.';
			return;
		}
		this.txUrlDialogError = null;
		const base = `${parsed.origin}${parsed.pathname}`;
		this.fhirBaseUrl = base.replace(/\/$/, '');
		const url = new URL(window.location.href);
		url.searchParams.set('tx', this.fhirBaseUrl);
		history.replaceState(null, '', url.pathname + url.search + url.hash);
		const modalEl = document.getElementById('txUrlModal');
		if (modalEl && typeof bootstrap !== 'undefined') {
			bootstrap.Modal.getOrCreateInstance(modalEl).hide();
		}
		if (!this.routingInitialized) {
			this.continueDashboardInit();
		} else {
			this.refreshAfterTxUrlChange();
		}
	},

	initFromHash() {
		const hash = window.location.hash.replace('#', '');
		if (hash) {
			const parts = hash.split('/');
			const section = parts[0];
			const tab = parts[1];
			if (section === 'resources') {
				this.section = 'resources';
				if (tab === 'codesystem' || tab === 'valueset' || tab === 'conceptmap') {
					this.tab = tab;
					this.loadTabIfNeeded();
				}
			} else if (section === 'syndication') {
				this.section = 'syndication';
				this.loadSyndicationIfNeeded();
			} else if (section === 'upload-sct') {
				this.section = 'upload-sct';
			}
		} else {
			this.section = 'resources';
			this.tab = 'codesystem';
			this.setHash();
			this.loadCodeSystems();
		}
	},

	setHash() {
		if (this.section === 'resources') {
			window.location.hash = `resources/${this.tab}`;
		} else {
			window.location.hash = this.section;
		}
	},

	switchTab(t) {
		this.tab = t;
		this.setHash();
		this.loadTabIfNeeded();
	},

	loadTabIfNeeded() {
		if (this.tab === 'codesystem' && this.codeSystems.length === 0 && !this.loadingCodesystems) {
			this.loadCodeSystems();
		} else if (this.tab === 'valueset' && this.valueSets.length === 0 && !this.loadingValueSets) {
			this.loadValueSets();
		} else if (this.tab === 'conceptmap' && this.conceptMaps.length === 0 && !this.loadingConceptMaps) {
			this.loadConceptMaps();
		}
	},

	loadSyndicationIfNeeded() {
		if (!this.syndicationAvailable) {
			return;
		}
		if (this.editions.length === 0 && !this.loadingSyndication) {
			this.loadSyndicationEditions();
		}
	}
};
