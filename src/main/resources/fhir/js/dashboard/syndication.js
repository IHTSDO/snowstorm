import { AJAX_TIMEOUT_MS, SYNDICATION_TIMEOUT_MS } from './constants.js';
import { fetchWithTimeout, errorMessage } from './http.js';
import { normalizeRow } from './resourceTransforms.js';

function refsetGroupKeyFromOption(opt) {
	if (opt.contentItemIdentifier) {
		return opt.contentItemIdentifier;
	}
	const uri = opt.contentItemVersion || '';
	const idx = uri.lastIndexOf('/version/');
	if (idx > 0) {
		return uri.substring(0, idx);
	}
	return uri;
}

export const dashboardSyndication = {
	buildRefsetDerivativeGroups(options) {
		const byId = new Map();
		for (const opt of options) {
			const id = refsetGroupKeyFromOption(opt);
			if (!id) {
				continue;
			}
			if (!byId.has(id)) {
				byId.set(id, {
					contentItemIdentifier: id,
					title: opt.title || 'N/A',
					versions: [],
					selectedVersion: ''
				});
			}
			byId.get(id).versions.push({
				versionDate: String(opt.versionDate),
				contentItemVersion: opt.contentItemVersion
			});
		}
		const groups = Array.from(byId.values());
		for (const g of groups) {
			g.versions.sort((a, b) => b.versionDate.localeCompare(a.versionDate));
		}
		groups.sort((a, b) => (a.title || '').localeCompare(b.title || '', undefined, { numeric: true }));
		return groups;
	},
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
		await this.syncActiveInstallationTasks();
	},

	async syncActiveInstallationTasks() {
		if (!this.syndicationAvailable) {
			return;
		}
		try {
			const res = await fetch('/syndication/active-installations');
			if (!res.ok) {
				return;
			}
			const tasks = await res.json();
			if (!Array.isArray(tasks)) {
				return;
			}
			for (const task of tasks) {
				const editionId = task.editionId;
				const taskId = task.taskId;
				const s = task.status;
				if (!editionId || !taskId) {
					continue;
				}
				try {
					const detailRes = await fetch(`/syndication/install/${taskId}`);
					if (detailRes.ok) {
						const detail = await detailRes.json();
						this.installTaskSnapshotByEditionId = { ...this.installTaskSnapshotByEditionId, [editionId]: detail };
					}
				} catch {
					/* keep going */
				}
				if (s === 'PENDING') {
					this.installState = { ...this.installState, [editionId]: { status: 'queued' } };
					this.beginInstallationPolling(taskId, editionId);
				} else if (s === 'IN_PROGRESS') {
					this.installState = { ...this.installState, [editionId]: { status: 'installing' } };
					this.beginInstallationPolling(taskId, editionId);
				}
			}
		} catch {
			/* ignore */
		}
	},

	installPackagesForEdition(editionId) {
		const t = this.installTaskSnapshotByEditionId[editionId];
		return (t && Array.isArray(t.packageProgress)) ? t.packageProgress : [];
	},

	/** yyyyMMdd from syndication content item URI, or empty string if not present */
	syndicationVersionDateFromContentItemVersion(contentItemVersion) {
		if (!contentItemVersion || typeof contentItemVersion !== 'string') {
			return '';
		}
		const m = contentItemVersion.match(/\/version\/(\d{8})(?:\/)?$/);
		return m ? m[1] : '';
	},

	syndicationPackageTitleWithDate(pkg) {
		const title = (pkg && pkg.title) ? pkg.title : '';
		const d = pkg ? this.syndicationVersionDateFromContentItemVersion(pkg.contentItemVersion) : '';
		if (!d) {
			return title;
		}
		return `${title} (${d})`;
	},

	syndicationInstallProgressVisible(editionId) {
		const st = this.getInstallStatus(editionId).status;
		if (st !== 'installing' && st !== 'queued') {
			return false;
		}
		return this.installPackagesForEdition(editionId).length > 0;
	},

	versioningProgressForEdition(editionId) {
		const t = this.installTaskSnapshotByEditionId[editionId];
		if (!t) {
			return 0;
		}
		const p = t.versioningProgressPercent;
		return typeof p === 'number' ? p : 0;
	},

	syndicationVersioningRowVisible(editionId) {
		return this.getInstallStatus(editionId).status === 'installing' && this.versioningProgressForEdition(editionId) > 0;
	},

	beginInstallationPolling(taskId, editionId) {
		if (!this._installationPollTaskIds) {
			this._installationPollTaskIds = new Set();
		}
		if (this._installationPollTaskIds.has(taskId)) {
			return;
		}
		this._installationPollTaskIds.add(taskId);
		const pollMs = 2000;
		const setInstallStatus = (status, error) => {
			this.installState = { ...this.installState, [editionId]: error != null ? { status, error } : { status } };
		};
		const finishPoll = () => {
			this._installationPollTaskIds.delete(taskId);
		};
		const poll = async () => {
			let taskRes;
			try {
				taskRes = await fetch(`/syndication/install/${taskId}`);
			} catch {
				setInstallStatus('failed', 'Unable to reach server');
				finishPoll();
				const snap = { ...this.installTaskSnapshotByEditionId };
				delete snap[editionId];
				this.installTaskSnapshotByEditionId = snap;
				return;
			}
			if (!taskRes.ok) {
				const notFound = taskRes.status === 404;
				setInstallStatus('failed', notFound ? 'Installation task not found' : 'Failed to load task status');
				finishPoll();
				const snap = { ...this.installTaskSnapshotByEditionId };
				delete snap[editionId];
				this.installTaskSnapshotByEditionId = snap;
				return;
			}
			let taskData;
			try {
				taskData = await taskRes.json();
			} catch {
				setInstallStatus('failed', 'Invalid task response');
				finishPoll();
				const snap = { ...this.installTaskSnapshotByEditionId };
				delete snap[editionId];
				this.installTaskSnapshotByEditionId = snap;
				return;
			}
			this.installTaskSnapshotByEditionId = { ...this.installTaskSnapshotByEditionId, [editionId]: taskData };
			const taskStatus = taskData.status;
			if (taskStatus === 'COMPLETED') {
				setInstallStatus('completed');
				finishPoll();
				const next = { ...this.installTaskSnapshotByEditionId };
				delete next[editionId];
				this.installTaskSnapshotByEditionId = next;
				alert('Installation completed successfully!');
				this.loadSyndicationEditions();
				return;
			}
			if (taskStatus === 'FAILED') {
				const errMsg = taskData.errorMessage || 'Installation failed';
				setInstallStatus('failed', errMsg);
				finishPoll();
				const next = { ...this.installTaskSnapshotByEditionId };
				delete next[editionId];
				this.installTaskSnapshotByEditionId = next;
				alert('Installation failed: ' + errMsg);
				return;
			}
			if (taskStatus === 'PENDING') {
				setInstallStatus('queued');
			} else if (taskStatus === 'IN_PROGRESS') {
				setInstallStatus('installing');
			}
			setTimeout(poll, pollMs);
		};
		setTimeout(poll, pollMs);
	},

	upgradeEdition(inst) {
		if (!inst.upgradeVersion) {
			return;
		}
		this.openSyndicationInstallDialog({
			id: inst.id,
			selectedVersion: inst.upgradeVersion,
			title: inst.title,
			versionsAvailable: []
		});
	},

	openSyndicationInstallDialog(edition) {
		if (!edition.selectedVersion) {
			alert('Please select a version');
			return;
		}
		this.pendingSyndicationEdition = edition;
		this.syndicationDerivativeGroups = [];
		this.syndicationDerivativesError = null;
		const el = document.getElementById('syndicationDerivativesModal');
		if (el && typeof bootstrap !== 'undefined') {
			bootstrap.Modal.getOrCreateInstance(el).show();
		}
		this.loadSyndicationDerivativeOptions();
	},

	async loadSyndicationDerivativeOptions() {
		const ed = this.pendingSyndicationEdition;
		if (!ed || !ed.selectedVersion) {
			return;
		}
		this.syndicationDerivativesLoading = true;
		this.syndicationDerivativesError = null;
		try {
			const params = new URLSearchParams({ editionId: ed.id, version: ed.selectedVersion });
			const res = await fetch(`/syndication/edition-derivatives?${params.toString()}`);
			let data;
			try {
				data = await res.json();
			} catch (_) {
				data = null;
			}
			if (!res.ok) {
				throw new Error((data && data.message) || 'Failed to load refset packages');
			}
			const flat = Array.isArray(data) ? data : [];
			this.syndicationDerivativeGroups = this.buildRefsetDerivativeGroups(flat);
		} catch (err) {
			this.syndicationDerivativesError = err.message || String(err);
			this.syndicationDerivativeGroups = [];
		} finally {
			this.syndicationDerivativesLoading = false;
		}
	},

	confirmSyndicationInstall() {
		const edition = this.pendingSyndicationEdition;
		const selected = [];
		for (const g of this.syndicationDerivativeGroups) {
			const pick = g.selectedVersion;
			if (!pick) {
				continue;
			}
			const ver = g.versions.find(v => v.versionDate === pick);
			if (ver) {
				selected.push(ver.contentItemVersion);
			}
		}
		const el = document.getElementById('syndicationDerivativesModal');
		if (el && typeof bootstrap !== 'undefined') {
			bootstrap.Modal.getOrCreateInstance(el).hide();
		}
		this.pendingSyndicationEdition = null;
		if (edition) {
			this.installEdition(edition, selected);
		}
	},

	cancelSyndicationInstall() {
		const el = document.getElementById('syndicationDerivativesModal');
		if (el && typeof bootstrap !== 'undefined') {
			bootstrap.Modal.getOrCreateInstance(el).hide();
		}
		this.pendingSyndicationEdition = null;
	},

	getInstallStatus(editionId) {
		return this.installState[editionId] || { status: 'idle' };
	},

	async installEdition(edition, derivativeContentItemVersions = []) {
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
				body: JSON.stringify({ editionId, version, derivativeContentItemVersions })
			});
			const data = await res.json();
			if (!res.ok) throw new Error(data.message || 'Error starting installation');
			const taskId = data.taskId;
			this.installTaskSnapshotByEditionId = { ...this.installTaskSnapshotByEditionId, [editionId]: data };
			this.beginInstallationPolling(taskId, editionId);
		} catch (err) {
			const errMsg = err.message || 'Error starting installation';
			setInstallStatus('failed', errMsg);
			const snap = { ...this.installTaskSnapshotByEditionId };
			delete snap[editionId];
			this.installTaskSnapshotByEditionId = snap;
			alert(errMsg);
		}
	}
};
