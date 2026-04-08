import { AJAX_TIMEOUT_MS, SYNDICATION_TIMEOUT_MS } from './constants.js';
import { fetchWithTimeout, errorMessage } from './http.js';
import { normalizeRow } from './resourceTransforms.js';

export const dashboardSyndication = {
	async loadSyndicationEditions() {
		this.loadingSyndication = true;
		this.loadingInstalledEditions = true;
		this.errorSyndication = null;
		this.errorInstalledEditions = null;
		let syndRes, csRes;
		try {
			[syndRes, csRes] = await Promise.all([
				fetchWithTimeout('/syndication/snomed-editions', SYNDICATION_TIMEOUT_MS),
				fetchWithTimeout(this.fhirBaseUrl + '/CodeSystem', AJAX_TIMEOUT_MS)
			]);
			const syndData = await syndRes.json();
			const csData = await csRes.json();
			if (!syndRes.ok) throw new Error(syndData.message || 'Failed to load editions');
			if (syndData && syndData.length > 0) {
				this.editions = syndData.map(ed => ({
					id: ed.id,
					title: ed.title || 'N/A',
					versionsAvailable: ed.versionsAvailable || [],
					selectedVersion: (ed.versionsAvailable && ed.versionsAvailable[0]) || ''
				}));
			} else {
				this.editions = [];
			}
			if (!csRes.ok) {
				this.errorInstalledEditions = errorMessage(
					new Error(csData.message || 'Failed to load CodeSystems'), 'CodeSystems', csRes
				);
				this.snomedCodeSystems = [];
			} else {
				this.errorInstalledEditions = null;
				const SNOMED_SCT_URL = 'http://snomed.info/sct';
				const versionPattern = /^https?:\/\/snomed\.info\/[xs]?sct\/\d+\/version\/\d{8}/;
				const entries = (csData.entry || []).map(e => e.resource).filter(r => r);
				this.snomedCodeSystems = entries.filter(cs => {
					const url = (cs.url || '').toString();
					const version = (cs.version || '').toString();
					return url.includes(SNOMED_SCT_URL) && versionPattern.test(version);
				}).map(cs => normalizeRow(cs));
			}
		} catch (err) {
			this.errorSyndication = errorMessage(err, 'editions', syndRes);
			this.editions = [];
			this.errorInstalledEditions = errorMessage(err, 'installed editions', csRes);
			this.snomedCodeSystems = [];
		} finally {
			this.loadingSyndication = false;
			this.loadingInstalledEditions = false;
		}
	},

	upgradeEdition(inst) {
		this.installEdition({
			id: inst.id,
			selectedVersion: inst.upgradeVersion,
			title: inst.title,
			versionsAvailable: []
		});
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
					this.loadSyndicationEditions();
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
	}
};
