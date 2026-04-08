function matchesTableFilter(row, query) {
	const q = (query || '').trim();
	if (!q) return true;
	const needle = q.toLowerCase();
	const fields = ['title', 'url', 'version', 'status', 'publisher', 'id'];
	return fields.some(f => String(row[f] ?? '').toLowerCase().includes(needle));
}

export const dashboardGetters = {
	get conceptMapDeleteSupported() {
		if (!this.capabilityStatement) return true;
		const rest = (this.capabilityStatement.rest || [])[0];
		if (!rest) return true;
		const conceptMapRes = (rest.resource || []).find(r => r.type === 'ConceptMap');
		if (!conceptMapRes) return true;
		return (conceptMapRes.interaction || []).some(i => i.code === 'delete');
	},

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
		return `${this.availableEditions.length} edition(s) available`;
	},

	get installedEditionsCountText() {
		if (this.loadingInstalledEditions) return 'Loading installed editions...';
		if (this.errorInstalledEditions) return 'Error loading installed editions';
		return `${this.sortedInstalledEditions.length} edition(s) installed`;
	},

	get sortedCodeSystems() {
		const filtered = this.codeSystems.filter(r =>
			matchesTableFilter(r, this.tableFilter.codesystem)
		);
		return this.sortedFor('codesystem', filtered);
	},
	get sortedValueSets() {
		const filtered = this.valueSets.filter(r =>
			matchesTableFilter(r, this.tableFilter.valueset)
		);
		return this.sortedFor('valueset', filtered);
	},
	get sortedConceptMaps() {
		const filtered = this.conceptMaps.filter(r =>
			matchesTableFilter(r, this.tableFilter.conceptmap)
		);
		return this.sortedFor('conceptmap', filtered);
	},

	/** True when every ConceptMap group has a non-empty source URI (or there are no groups). */
	get addConceptMapGroupSourcesComplete() {
		const payload = this.addConceptMapPayload;
		if (!payload) return false;
		const groups = payload.group;
		if (!Array.isArray(groups) || groups.length === 0) return true;
		const sources = this.addConceptMapGroupSources || [];
		for (let i = 0; i < groups.length; i++) {
			if (!String(sources[i] ?? '').trim()) return false;
		}
		return true;
	},
	get installedEditions() {
		const SNOMED_SCT_URL = 'http://snomed.info/sct';
		const versionPattern = /^https?:\/\/snomed\.info\/[xs]?sct\/(\d+)\/version\/(\d{8})/;
		const byEdition = {};
		for (const cs of this.snomedCodeSystems) {
			const url = (cs.url || '').toString();
			const version = (cs.version || '').toString();
			if (!url.includes(SNOMED_SCT_URL) || !version) continue;
			const m = version.match(versionPattern);
			if (!m) continue;
			const editionId = 'http://snomed.info/sct/' + m[1];
			const versionDate = m[2];
			if (!byEdition[editionId]) {
				byEdition[editionId] = { id: editionId, title: cs.title || 'N/A', versions: [] };
			}
			byEdition[editionId].versions.push(versionDate);
			if (versionDate > (byEdition[editionId].latestVersionDate || '')) {
				byEdition[editionId].latestVersionDate = versionDate;
				byEdition[editionId].title = cs.title || byEdition[editionId].title;
			}
		}
		const syndicationById = {};
		for (const ed of this.editions) {
			syndicationById[ed.id] = ed;
		}
		return Object.values(byEdition).map(inst => {
			const versions = [...new Set(inst.versions)].sort().reverse();
			const latestVersion = versions[0] || '';
			const feed = syndicationById[inst.id];
			let upgradeVersion = null;
			if (feed && feed.versionsAvailable && feed.versionsAvailable.length > 0) {
				const latestAvailable = feed.versionsAvailable[0];
				if (latestAvailable && latestAvailable > latestVersion) {
					upgradeVersion = latestAvailable;
				}
			}
			return {
				id: inst.id,
				title: inst.title,
				latestVersion,
				upgradeVersion
			};
		});
	},

	get sortedInstalledEditions() {
		return [...this.installedEditions].sort((a, b) =>
			(a.title || '').localeCompare(b.title || '', undefined, { numeric: true })
		);
	},

	get availableEditions() {
		const installedIds = new Set(this.installedEditions.map(e => e.id));
		const withoutRefsets = this.editions.filter(
			ed => !(ed.title && ed.title.toLowerCase().includes('refset'))
		);
		return withoutRefsets.filter(ed => !installedIds.has(ed.id));
	},

	get sortedEditions() {
		return this.sortedFor('syndication', this.availableEditions);
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
	}
};
