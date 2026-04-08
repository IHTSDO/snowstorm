import { CONCEPTMAP_DEFAULT_GROUP_SOURCE } from './dashboard/constants.js';
import { dashboardCapability } from './dashboard/capability.js';
import { dashboardConceptMapUi } from './dashboard/conceptMapUi.js';
import { dashboardGetters } from './dashboard/getters.js';
import { dashboardModalDetail } from './dashboard/modalDetail.js';
import { dashboardResources } from './dashboard/resources.js';
import { dashboardRouting } from './dashboard/routing.js';
import { dashboardSyndication } from './dashboard/syndication.js';

function createDashboardState() {
	return {
		fhirBaseUrl: 'http://localhost:8080/fhir',
		section: 'resources',
		tab: 'codesystem',
		codeSystems: [],
		valueSets: [],
		conceptMaps: [],
		editions: [],
		snomedCodeSystems: [],
		loadingCodesystems: false,
		loadingValueSets: false,
		loadingConceptMaps: false,
		loadingSyndication: false,
		loadingInstalledEditions: false,
		errorCodesystems: null,
		errorValueSets: null,
		errorConceptMaps: null,
		errorSyndication: null,
		errorInstalledEditions: null,
		codeSystemWarnings: [],
		showCodeSystemWarningsDetails: false,
		installState: {},
		sortKey: { codesystem: 'title', valueset: 'title', conceptmap: 'title', syndication: 'title' },
		sortAsc: { codesystem: true, valueset: true, conceptmap: true, syndication: true },
		tableFilter: { codesystem: '', valueset: '', conceptmap: '' },
		modalType: null,
		modalDetail: null,
		modalLoading: false,
		modalError: null,
		showAddValueSetForm: false,
		addValueSetJson: '',
		addValueSetError: null,
		addValueSetSaving: false,
		showAddConceptMapForm: false,
		addConceptMapPayload: null,
		addConceptMapUrl: '',
		addConceptMapVersion: '',
		addConceptMapTitle: '',
		addConceptMapName: '',
		addConceptMapStatus: 'draft',
		addConceptMapDescription: '',
		addConceptMapExperimental: false,
		addConceptMapGroupSources: [],
		conceptMapDefaultGroupSource: CONCEPTMAP_DEFAULT_GROUP_SOURCE,
		addConceptMapError: null,
		addConceptMapSaving: false,
		deleteConfirmId: null,
		deleteConfirmTitle: null,
		deletingConceptMap: false,
		deleteConceptMapError: null,
		capabilityStatement: null,
		syndicationAvailable: true,
		txUrlPrompt: '',
		txUrlDialogError: null,
		routingInitialized: false,
		conceptMapTranslateExampleBusy: false
	};
}

document.addEventListener('alpine:init', () => {
	Alpine.data('dashboard', () => {
		// Do not spread dashboardGetters: object spread invokes getters and copies
		// snapshot values with the wrong `this`. Preserve accessors via descriptors.
		const component = {
			...createDashboardState(),
			...dashboardRouting,
			...dashboardCapability,
			...dashboardSyndication,
			...dashboardResources,
			...dashboardConceptMapUi,
			...dashboardModalDetail
		};
		return Object.defineProperties(
			component,
			Object.getOwnPropertyDescriptors(dashboardGetters)
		);
	});
});
