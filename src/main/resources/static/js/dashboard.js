document.addEventListener('alpine:init', () => {
	Alpine.data('dashboard', () => {
		const AJAX_TIMEOUT_MS = 60000;
		const SYNDICATION_TIMEOUT_MS = 10000;

		return {
			section: 'resources',
			tab: 'codesystem',
			codeSystems: [],
			valueSets: [],
			conceptMaps: [],
			editions: [],
			loadingCodesystems: false,
			loadingValueSets: false,
			loadingConceptMaps: false,
			loadingSyndication: false,
			errorCodesystems: null,
			errorValueSets: null,
			errorConceptMaps: null,
			errorSyndication: null,
			installState: {},
			sortKey: { codesystem: 'title', valueset: 'title', conceptmap: 'title', syndication: 'title' },
			sortAsc: { codesystem: true, valueset: true, conceptmap: true, syndication: true },
			modalType: null,
			modalDetail: null,
			modalLoading: false,
			modalError: null,

			get resourceCountText() {
				if (this.tab === 'codesystem') {
					if (this.loadingCodesystems) return 'Loading CodeSystems...';
					return `${this.codeSystems.length} CodeSystem(s) loaded`;
				}
				if (this.tab === 'valueset') {
					if (this.loadingValueSets) return 'Loading ValueSets...';
					return `${this.valueSets.length} ValueSet(s) loaded`;
				}
				if (this.tab === 'conceptmap') {
					if (this.loadingConceptMaps) return 'Loading ConceptMaps...';
					return `${this.conceptMaps.length} ConceptMap(s) loaded`;
				}
				return '';
			},

			get syndicationCountText() {
				if (this.loadingSyndication) return 'Loading editions...';
				if (this.errorSyndication) return 'Error loading data';
				return `${this.editions.length} edition(s) loaded`;
			},

			get sortedCodeSystems() {
				return this.sortedFor('codesystem', this.codeSystems);
			},
			get sortedValueSets() {
				return this.sortedFor('valueset', this.valueSets);
			},
			get sortedConceptMaps() {
				return this.sortedFor('conceptmap', this.conceptMaps);
			},
			get sortedEditions() {
				return this.sortedFor('syndication', this.editions);
			},

			sortedFor(type, arr) {
				const key = this.sortKey[type] || 'title';
				const asc = this.sortAsc[type];
				return [...arr].sort((a, b) => {
					const va = (a[key] || '').toString();
					const vb = (b[key] || '').toString();
					const cmp = va.localeCompare(vb, undefined, { numeric: true });
					return asc ? cmp : -cmp;
				});
			},

			sortBy(type, key) {
				if (this.sortKey[type] === key) {
					this.sortAsc[type] = !this.sortAsc[type];
				} else {
					this.sortKey[type] = key;
					this.sortAsc[type] = true;
				}
			},

			init() {
				window.addEventListener('hashchange', () => this.initFromHash());
				this.initFromHash();
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
				if (this.editions.length === 0 && !this.loadingSyndication) {
					this.loadSyndicationEditions();
				}
			},

			async loadSyndicationEditions() {
				this.loadingSyndication = true;
				this.errorSyndication = null;
				let res;
				try {
					res = await this.fetchWithTimeout('/syndication/snomed-editions', SYNDICATION_TIMEOUT_MS);
					const data = await res.json();
					if (!res.ok) throw new Error(data.message || 'Failed to load editions');
					if (data && data.length > 0) {
						this.editions = data.map(ed => ({
							id: ed.id,
							title: ed.title || 'N/A',
							versionsAvailable: ed.versionsAvailable || [],
							selectedVersion: (ed.versionsAvailable && ed.versionsAvailable[0]) || ''
						}));
					} else {
						this.editions = [];
					}
				} catch (err) {
					this.errorSyndication = this.errorMessage(err, 'editions', res);
					this.editions = [];
				} finally {
					this.loadingSyndication = false;
				}
			},

			getInstallStatus(editionId) {
				return this.installState[editionId] || { status: 'idle' };
			},

			async installEdition(edition) {
				const version = edition.selectedVersion;
				if (!version) {
					alert('Please select a version');
					return;
				}
				const editionId = edition.id;
				const setInstallStatus = (status, error) => {
					this.installState = { ...this.installState, [editionId]: error != null ? { status, error } : { status } };
				};
				setInstallStatus('queued');
				try {
					const res = await fetch('/syndication/install', {
						method: 'POST',
						headers: { 'Content-Type': 'application/json' },
						body: JSON.stringify({ editionId, version })
					});
					const data = await res.json();
					if (!res.ok) throw new Error(data.message || 'Error starting installation');
					const taskId = data.taskId;
					setInstallStatus('installing');
					const pollMs = 2000;
					const maxWaitMs = 600000;
					const started = Date.now();
					const poll = async () => {
						const taskRes = await fetch(`/syndication/install/${taskId}`);
						const taskData = await taskRes.json();
						const taskStatus = taskData.status;
						if (taskStatus === 'COMPLETED') {
							setInstallStatus('completed');
							alert('Installation completed successfully!');
							return;
						}
						if (taskStatus === 'FAILED') {
							const errMsg = taskData.errorMessage || 'Installation failed';
							setInstallStatus('failed', errMsg);
							alert('Installation failed: ' + errMsg);
							return;
						}
						if (Date.now() - started < maxWaitMs) {
							setTimeout(poll, pollMs);
						} else {
							setInstallStatus('failed', 'Timeout');
						}
					};
					setTimeout(poll, pollMs);
				} catch (err) {
					const errMsg = err.message || 'Error starting installation';
					setInstallStatus('failed', errMsg);
					alert(errMsg);
				}
			},

			async loadCodeSystems() {
				this.loadingCodesystems = true;
				this.errorCodesystems = null;
				let res;
				try {
					res = await this.fetchWithTimeout('/fhir/CodeSystem', AJAX_TIMEOUT_MS);
					const data = await res.json();
					if (!res.ok) throw new Error(data.message || 'Failed to load CodeSystems');
					if (data.entry && data.entry.length > 0) {
						this.codeSystems = data.entry.map(e => this.normalizeRow(e.resource));
					} else {
						this.codeSystems = [];
					}
				} catch (err) {
					this.errorCodesystems = this.errorMessage(err, 'CodeSystems', res);
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
					res = await this.fetchWithTimeout('/fhir/ValueSet', AJAX_TIMEOUT_MS);
					const data = await res.json();
					if (!res.ok) throw new Error(data.message || 'Failed to load ValueSets');
					if (data.entry && data.entry.length > 0) {
						this.valueSets = data.entry.map(e => this.normalizeRow(e.resource));
					} else {
						this.valueSets = [];
					}
				} catch (err) {
					this.errorValueSets = this.errorMessage(err, 'ValueSets', res);
					this.valueSets = [];
				} finally {
					this.loadingValueSets = false;
				}
			},

			async loadConceptMaps() {
				this.loadingConceptMaps = true;
				this.errorConceptMaps = null;
				let res;
				try {
					res = await this.fetchWithTimeout('/fhir/ConceptMap', AJAX_TIMEOUT_MS);
					const data = await res.json();
					if (!res.ok) throw new Error(data.message || 'Failed to load ConceptMaps');
					if (data.entry && data.entry.length > 0) {
						this.conceptMaps = data.entry.map(e => this.normalizeRow(e.resource));
					} else {
						this.conceptMaps = [];
					}
				} catch (err) {
					this.errorConceptMaps = this.errorMessage(err, 'ConceptMaps', res);
					this.conceptMaps = [];
				} finally {
					this.loadingConceptMaps = false;
				}
			},

			normalizeRow(resource) {
				return {
					id: resource.id,
					title: resource.title || resource.name || 'N/A',
					url: resource.url || 'N/A',
					version: resource.version || 'N/A',
					status: resource.status || 'N/A',
					publisher: resource.publisher || 'N/A'
				};
			},

			fetchWithTimeout(url, ms) {
				const ctrl = new AbortController();
				const t = setTimeout(() => ctrl.abort(), ms);
				return fetch(url, { signal: ctrl.signal }).finally(() => clearTimeout(t));
			},

			errorMessage(err, label, res) {
				if (err.name === 'AbortError') return 'Request timed out. Please try again.';
				if (res) {
					if (res.status === 404) return 'FHIR endpoint not found. Please check if the server is running.';
					if (res.status === 500) return 'Server error. Please try again later.';
				}
				return err.message || `Error loading ${label}`;
			},

			async viewDetail(type, id) {
				this.modalType = type;
				this.modalLoading = true;
				this.modalDetail = null;
				this.modalError = null;
				const modalEl = document.getElementById(type + 'Modal');
				const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
				modal.show();

				const url = `/fhir/${type === 'codesystem' ? 'CodeSystem' : type === 'valueset' ? 'ValueSet' : 'ConceptMap'}/${id}`;
				let res;
				try {
					res = await this.fetchWithTimeout(url, AJAX_TIMEOUT_MS);
					const data = await res.json();
					if (!res.ok) throw new Error(data.message || 'Not found');
					this.modalDetail = data;
				} catch (err) {
					if (err.name === 'AbortError') this.modalError = 'Request timed out. Please try again.';
					else if (typeof res !== 'undefined' && res.status === 404) this.modalError = `${type === 'codesystem' ? 'CodeSystem' : type === 'valueset' ? 'ValueSet' : 'ConceptMap'} not found.`;
					else if (typeof res !== 'undefined' && res.status === 500) this.modalError = 'Server error. Please try again later.';
					else this.modalError = err.message || 'Error loading details';
				} finally {
					this.modalLoading = false;
				}
			}
		};
	});
});
