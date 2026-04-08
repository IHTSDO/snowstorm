export function normalizeRow(resource) {
	return {
		id: resource.id,
		title: resource.title || resource.name || 'N/A',
		url: resource.url || 'N/A',
		version: resource.version || 'N/A',
		status: resource.status || 'N/A',
		publisher: resource.publisher || 'N/A'
	};
}

export function dedupeCodeSystemsByUrlVersion(rows) {
	const byKey = new Map();
	for (const cs of rows) {
		const url = (cs.url || '').toString();
		const version = (cs.version || '').toString();
		const key = url + '|' + version;
		if (!byKey.has(key)) byKey.set(key, []);
		byKey.get(key).push(cs);
	}

	const kept = [];
	const warnings = [];
	let seq = 0;

	for (const [, items] of byKey.entries()) {
		if (!items || items.length <= 1) {
			kept.push(items[0]);
			continue;
		}

		const internationalCandidates = items.filter(it =>
			(it.title || '').toString().toLowerCase().includes('international edition')
		);
		const keep = internationalCandidates.length > 0 ? internationalCandidates[0] : items[0];
		kept.push(keep);

		for (const discard of items) {
			if (discard === keep) continue;
			warnings.push({
				seq: seq++,
				url: (discard.url || '').toString(),
				version: (discard.version || '').toString(),
				keptTitle: (keep.title || '').toString(),
				discardedTitle: (discard.title || '').toString()
			});
		}
	}

	return { kept, warnings };
}
