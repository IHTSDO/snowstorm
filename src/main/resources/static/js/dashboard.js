$(document).ready(function () {

    $(window).bind('load', function() {
        $.url = window.location.href; // update the jQuery URL cache
    });

    const $tableBody = $('#codesystem-table tbody');
    const $valuesetTableBody = $('#valueset-table tbody');
    const $conceptmapTableBody = $('#conceptmap-table tbody');

    const AJAX_TIMEOUT_MS = 60000; // 1 minute

    // Handle browser back/forward buttons
    $(window).on('hashchange', function() {
        initializeFromHash();
    });

    // Handler for FHIR Resources button click
    $('#fhir-resources-btn').on('click', function(e) {
        e.preventDefault();
        
        // Hide all tabs then show the #resources tab
        $('.tab-content').hide();
        $('#resources').show();
        
        // Update URL hash
        window.location.hash = 'resources';
    });

    // Handler for Upload SNOMED CT button click
    $('#upload-sct-btn').on('click', function(e) {
        e.preventDefault();
        
        // Hide all tabs then show the #upload-sct tab
        $('.tab-content').hide();
        $('#upload-sct').show();
        
        // Update URL hash
        window.location.hash = 'upload-sct';
    });

    // Tab switching functionality
    $('#resource-tabs .nav-link').on('click', function(e) {
        e.preventDefault();
        
        // Remove active class from all tabs and hide all tab panes
        $('#resource-tabs .nav-link').removeClass('active');
        $('.tab-pane').hide();
        
        // Add active class to clicked tab
        $(this).addClass('active');
        
        // Show corresponding tab pane
        const targetTab = $(this).attr('href');
        $(targetTab).show();
        
        // Show/hide appropriate refresh buttons
        const tabType = $(this).data('tab');
        $('[id^="refresh-"]').hide();
        $(`#refresh-${tabType}s`).show();
        
        // Load data for the selected tab if not already loaded
        if (tabType === 'codesystem') {
            if ($tableBody.find('tr').length === 0) {
                loadCodeSystems();
            }
        } else if (tabType === 'valueset') {
            if ($valuesetTableBody.find('tr').length === 0) {
                loadValueSets();
            }
        } else if (tabType === 'conceptmap') {
            if ($conceptmapTableBody.find('tr').length === 0) {
                loadConceptMaps();
            }
        }
        
        // Update count display
        updateResourceCount(tabType);
        
        // Update URL hash with both section and tab
        window.location.hash = `resources/${tabType}`;
    });

    // Handler for refresh CodeSystems button click
    $('#refresh-codesystems').on('click', function() {
        loadCodeSystems();
    });

    // Handler for refresh ValueSets button click
    $('#refresh-valuesets').on('click', function() {
        loadValueSets();
    });

    // Handler for refresh ConceptMaps button click
    $('#refresh-conceptmaps').on('click', function() {
        loadConceptMaps();
    });

    // Function to update resource count display (excludes loading rows)
    function updateResourceCount(tabType) {
        const countElement = $('#resource-count');
        if (tabType === 'codesystem') {
            const totalRows = $tableBody.find('tr').length;
            const loadingRows = $tableBody.find('tr[data-loading]').length;
            if (loadingRows > 0 && totalRows === loadingRows) {
                countElement.text('Loading CodeSystems...');
            } else {
                const count = totalRows - loadingRows;
                countElement.text(`${count} CodeSystem(s) loaded`);
            }
        } else if (tabType === 'valueset') {
            const totalRows = $valuesetTableBody.find('tr').length;
            const loadingRows = $valuesetTableBody.find('tr[data-loading]').length;
            if (loadingRows > 0 && totalRows === loadingRows) {
                countElement.text('Loading ValueSets...');
            } else {
                const count = totalRows - loadingRows;
                countElement.text(`${count} ValueSet(s) loaded`);
            }
        } else if (tabType === 'conceptmap') {
            const totalRows = $conceptmapTableBody.find('tr').length;
            const loadingRows = $conceptmapTableBody.find('tr[data-loading]').length;
            if (loadingRows > 0 && totalRows === loadingRows) {
                countElement.text('Loading ConceptMaps...');
            } else {
                const count = totalRows - loadingRows;
                countElement.text(`${count} ConceptMap(s) loaded`);
            }
        }
    }

    // Function to load CodeSystem data from FHIR API
    function loadCodeSystems() {
        // Clear existing table data
        $tableBody.empty();
        
        // Update count to loading
        $('#resource-count').text('Loading CodeSystems...');
        
        // Show loading state
        $tableBody.append(`
            <tr data-loading>
                <td colspan="6" class="text-center">
                    <div class="spinner-border spinner-border-sm" role="status">
                        <span class="visually-hidden">Loading...</span>
                    </div>
                    Loading CodeSystems...
                </td>
            </tr>
        `);

        // Make API call to FHIR endpoint
        $.ajax({
            url: '/fhir/CodeSystem',
            method: 'GET',
            dataType: 'json',
            timeout: AJAX_TIMEOUT_MS,
            success: function(data) {
                // Clear loading state
                $tableBody.empty();
                
                if (data.entry && data.entry.length > 0) {
                    // Update count
                    $('#resource-count').text(`${data.entry.length} CodeSystem(s) loaded`);
                    
                    data.entry.forEach(function(entry) {
                        const codeSystem = entry.resource;
                        const title = codeSystem.title || codeSystem.name || 'N/A';
                        const url = codeSystem.url || 'N/A';
                        const version = codeSystem.version || 'N/A';
                        const status = codeSystem.status || 'N/A';
                        const publisher = codeSystem.publisher || 'N/A';
                        
                        $tableBody.append(`
                            <tr>
                                <td>${title}</td>
                                <td>${url}</td>
                                <td>${version}</td>
                                <td>
                                    <span class="badge ${status === 'active' ? 'bg-success' : 'bg-secondary'}">${status}</span>
                                </td>
                                <td>${publisher}</td>
                                <td>
                                    <button class="btn btn-sm btn-outline-primary" title="View" onclick="viewCodeSystem('${codeSystem.id}')">
                                        <i class="bi bi-eye"></i>
                                    </button>
                                </td>
                            </tr>
                        `);
                    });
                } else {
                    $('#resource-count').text('No CodeSystems found');
                    $tableBody.append(`
                        <tr>
                            <td colspan="6" class="text-center text-muted">
                                No CodeSystems found
                            </td>
                        </tr>
                    `);
                }
                
                // Reinitialize DataTable
                if ($.fn.DataTable.isDataTable('#codesystem-table')) {
                    $('#codesystem-table').DataTable().destroy();
                }
                $('#codesystem-table').DataTable();
            },
            error: function(xhr, status, error) {
                // Clear loading state
                $tableBody.empty();
                
                // Update count to error state
                $('#resource-count').text('Error loading data');
                
                let errorMessage = 'Error loading CodeSystems';
                if (status === 'timeout') {
                    errorMessage = 'Request timed out. Please try again.';
                } else if (xhr.status === 404) {
                    errorMessage = 'FHIR endpoint not found. Please check if the server is running.';
                } else if (xhr.status === 500) {
                    errorMessage = 'Server error. Please try again later.';
                } else if (error) {
                    errorMessage = `Error: ${error}`;
                }
                
                // Show error message
                $tableBody.append(`
                    <tr>
                        <td colspan="6" class="text-center text-danger">
                            <i class="bi bi-exclamation-triangle"></i>
                            ${errorMessage}
                        </td>
                    </tr>
                `);
                
                console.error('Error loading CodeSystems:', {xhr, status, error});
            }
        });
    }

    // Function to load ValueSet data from FHIR API
    function loadValueSets() {
        // Clear existing table data
        $valuesetTableBody.empty();
        
        // Update count to loading
        $('#resource-count').text('Loading ValueSets...');
        
        // Show loading state
        $valuesetTableBody.append(`
            <tr data-loading>
                <td colspan="6" class="text-center">
                    <div class="spinner-border spinner-border-sm" role="status">
                        <span class="visually-hidden">Loading...</span>
                    </div>
                    Loading ValueSets...
                </td>
            </tr>
        `);

        // Make API call to FHIR endpoint
        $.ajax({
            url: '/fhir/ValueSet',
            method: 'GET',
            dataType: 'json',
            timeout: AJAX_TIMEOUT_MS,
            success: function(data) {
                // Clear loading state
                $valuesetTableBody.empty();
                
                if (data.entry && data.entry.length > 0) {
                    // Update count
                    $('#resource-count').text(`${data.entry.length} ValueSet(s) loaded`);
                    
                    data.entry.forEach(function(entry) {
                        const valueSet = entry.resource;
                        const title = valueSet.title || valueSet.name || 'N/A';
                        const url = valueSet.url || 'N/A';
                        const version = valueSet.version || 'N/A';
                        const status = valueSet.status || 'N/A';
                        const publisher = valueSet.publisher || 'N/A';
                        
                        $valuesetTableBody.append(`
                            <tr>
                                <td>${title}</td>
                                <td>${url}</td>
                                <td>${version}</td>
                                <td>
                                    <span class="badge ${status === 'active' ? 'bg-success' : 'bg-secondary'}">${status}</span>
                                </td>
                                <td>${publisher}</td>
                                <td>
                                    <button class="btn btn-sm btn-outline-primary" title="View" onclick="viewValueSet('${valueSet.id}')">
                                        <i class="bi bi-eye"></i>
                                    </button>
                                </td>
                            </tr>
                        `);
                    });
                } else {
                    $('#resource-count').text('No ValueSets found');
                    $valuesetTableBody.append(`
                        <tr>
                            <td colspan="6" class="text-center text-muted">
                                No ValueSets found
                            </td>
                        </tr>
                    `);
                }
                
                // Reinitialize DataTable
                if ($.fn.DataTable.isDataTable('#valueset-table')) {
                    $('#valueset-table').DataTable().destroy();
                }
                $('#valueset-table').DataTable();
            },
            error: function(xhr, status, error) {
                // Clear loading state
                $valuesetTableBody.empty();
                
                // Update count to error state
                $('#resource-count').text('Error loading data');
                
                let errorMessage = 'Error loading ValueSets';
                if (status === 'timeout') {
                    errorMessage = 'Request timed out. Please try again.';
                } else if (xhr.status === 404) {
                    errorMessage = 'FHIR endpoint not found. Please check if the server is running.';
                } else if (xhr.status === 500) {
                    errorMessage = 'Server error. Please try again later.';
                } else if (error) {
                    errorMessage = `Error: ${error}`;
                }
                
                // Show error message
                $valuesetTableBody.append(`
                    <tr>
                        <td colspan="6" class="text-center text-danger">
                            <i class="bi bi-exclamation-triangle"></i>
                            ${errorMessage}
                        </td>
                    </tr>
                `);
                
                console.error('Error loading ValueSets:', {xhr, status, error});
            }
        });
    }

    // Function to load ConceptMap data from FHIR API
    function loadConceptMaps() {
        // Clear existing table data
        $conceptmapTableBody.empty();
        
        // Update count to loading
        $('#resource-count').text('Loading ConceptMaps...');
        
        // Show loading state
        $conceptmapTableBody.append(`
            <tr data-loading>
                <td colspan="6" class="text-center">
                    <div class="spinner-border spinner-border-sm" role="status">
                        <span class="visually-hidden">Loading...</span>
                    </div>
                    Loading ConceptMaps...
                </td>
            </tr>
        `);

        // Make API call to FHIR endpoint
        $.ajax({
            url: '/fhir/ConceptMap',
            method: 'GET',
            dataType: 'json',
            timeout: AJAX_TIMEOUT_MS,
            success: function(data) {
                // Clear loading state
                $conceptmapTableBody.empty();
                
                if (data.entry && data.entry.length > 0) {
                    // Update count
                    $('#resource-count').text(`${data.entry.length} ConceptMap(s) loaded`);
                    
                    data.entry.forEach(function(entry) {
                        const conceptMap = entry.resource;
                        const title = conceptMap.title || conceptMap.name || 'N/A';
                        const url = conceptMap.url || 'N/A';
                        const version = conceptMap.version || 'N/A';
                        const status = conceptMap.status || 'N/A';
                        const publisher = conceptMap.publisher || 'N/A';
                        
                        $conceptmapTableBody.append(`
                            <tr>
                                <td>${title}</td>
                                <td>${url}</td>
                                <td>${version}</td>
                                <td>
                                    <span class="badge ${status === 'active' ? 'bg-success' : 'bg-secondary'}">${status}</span>
                                </td>
                                <td>${publisher}</td>
                                <td>
                                    <button class="btn btn-sm btn-outline-primary" title="View" onclick="viewConceptMap('${conceptMap.id}')">
                                        <i class="bi bi-eye"></i>
                                    </button>
                                </td>
                            </tr>
                        `);
                    });
                } else {
                    $('#resource-count').text('No ConceptMaps found');
                    $conceptmapTableBody.append(`
                        <tr>
                            <td colspan="6" class="text-center text-muted">
                                No ConceptMaps found
                            </td>
                        </tr>
                    `);
                }
                
                // Reinitialize DataTable
                if ($.fn.DataTable.isDataTable('#conceptmap-table')) {
                    $('#conceptmap-table').DataTable().destroy();
                }
                $('#conceptmap-table').DataTable();
            },
            error: function(xhr, status, error) {
                // Clear loading state
                $conceptmapTableBody.empty();
                
                // Update count to error state
                $('#resource-count').text('Error loading data');
                
                let errorMessage = 'Error loading ConceptMaps';
                if (status === 'timeout') {
                    errorMessage = 'Request timed out. Please try again.';
                } else if (xhr.status === 404) {
                    errorMessage = 'FHIR endpoint not found. Please check if the server is running.';
                } else if (xhr.status === 500) {
                    errorMessage = 'Server error. Please try again later.';
                } else if (error) {
                    errorMessage = `Error: ${error}`;
                }
                
                // Show error message
                $conceptmapTableBody.append(`
                    <tr>
                        <td colspan="6" class="text-center text-danger">
                            <i class="bi bi-exclamation-triangle"></i>
                            ${errorMessage}
                        </td>
                    </tr>
                `);
                
                console.error('Error loading ConceptMaps:', {xhr, status, error});
            }
        });
    }

    // Function to initialize state from URL hash
    function initializeFromHash() {
        const hash = window.location.hash.replace('#', '');
        
        if (hash) {
            const parts = hash.split('/');
            const section = parts[0];
            const tab = parts[1];
            
            // Handle section (sidebar)
            if (section === 'resources') {
                $('.tab-content').hide();
                $('#resources').show();
            } else if (section === 'upload-sct') {
                $('.tab-content').hide();
                $('#upload-sct').show();
            }
            
            // Handle tab within resources section
            if (section === 'resources' && tab) {
                // Remove active class from all tabs and hide all tab panes
                $('#resource-tabs .nav-link').removeClass('active');
                $('.tab-pane').hide();
                
                // Activate the correct tab
                const targetTab = `#${tab}-tab`;
                $(`[href="${targetTab}"]`).addClass('active');
                $(targetTab).show();
                
                // Show/hide appropriate refresh buttons
                $('[id^="refresh-"]').hide();
                $(`#refresh-${tab}s`).show();
                
                // Load data for the selected tab
                if (tab === 'codesystem') {
                    loadCodeSystems();
                } else if (tab === 'valueset') {
                    loadValueSets();
                } else if (tab === 'conceptmap') {
                    loadConceptMaps();
                }
                
                // Update count display
                updateResourceCount(tab);
            }
        } else {
            // Default state: show resources with codesystem tab
            $('.tab-content').hide();
            $('#resources').show();
            loadCodeSystems();
            
            // Set initial hash for browser history
            window.location.hash = 'resources/codesystem';
        }
    }

    // Initialize state from URL hash
    initializeFromHash();

    // Global functions for action buttons
    window.viewCodeSystem = function(id) {
        console.log('View CodeSystem:', id);
        
        // Show loading state in modal
        $('#codesystemDetails').html(`
            <div class="text-center">
                <div class="spinner-border" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
                <p class="mt-2">Loading CodeSystem details...</p>
            </div>
        `);
        
        // Show the modal
        const modal = new bootstrap.Modal(document.getElementById('codesystemModal'));
        modal.show();
        
        // Fetch CodeSystem details from API
        $.ajax({
            url: `/fhir/CodeSystem/${id}`,
            method: 'GET',
            dataType: 'json',
            timeout: AJAX_TIMEOUT_MS,
            success: function(data) {
                displayCodeSystemDetails(data);
            },
            error: function(xhr, status, error) {
                let errorMessage = 'Error loading CodeSystem details';
                if (status === 'timeout') {
                    errorMessage = 'Request timed out. Please try again.';
                } else if (xhr.status === 404) {
                    errorMessage = 'CodeSystem not found.';
                } else if (xhr.status === 500) {
                    errorMessage = 'Server error. Please try again later.';
                } else if (error) {
                    errorMessage = `Error: ${error}`;
                }
                
                $('#codesystemDetails').html(`
                    <div class="alert alert-danger">
                        <i class="bi bi-exclamation-triangle"></i>
                        ${errorMessage}
                    </div>
                `);
                
                console.error('Error loading CodeSystem details:', {xhr, status, error});
            }
        });
    };



    window.viewValueSet = function(id) {
        console.log('View ValueSet:', id);
        
        // Show loading state in modal
        $('#valuesetDetails').html(`
            <div class="text-center">
                <div class="spinner-border" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
                <p class="mt-2">Loading ValueSet details...</p>
            </div>
        `);
        
        // Show the modal
        const modal = new bootstrap.Modal(document.getElementById('valuesetModal'));
        modal.show();
        
        // Fetch ValueSet details from API
        $.ajax({
            url: `/fhir/ValueSet/${id}`,
            method: 'GET',
            dataType: 'json',
            timeout: AJAX_TIMEOUT_MS,
            success: function(data) {
                displayValueSetDetails(data);
            },
            error: function(xhr, status, error) {
                let errorMessage = 'Error loading ValueSet details';
                if (status === 'timeout') {
                    errorMessage = 'Request timed out. Please try again.';
                } else if (xhr.status === 404) {
                    errorMessage = 'ValueSet not found.';
                } else if (xhr.status === 500) {
                    errorMessage = 'Server error. Please try again later.';
                } else if (error) {
                    errorMessage = `Error: ${error}`;
                }
                
                $('#valuesetDetails').html(`
                    <div class="alert alert-danger">
                        <i class="bi bi-exclamation-triangle"></i>
                        ${errorMessage}
                    </div>
                `);
                
                console.error('Error loading ValueSet details:', {xhr, status, error});
            }
        });
    };



    window.viewConceptMap = function(id) {
        console.log('View ConceptMap:', id);
        
        // Show loading state in modal
        $('#conceptmapDetails').html(`
            <div class="text-center">
                <div class="spinner-border" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
                <p class="mt-2">Loading ConceptMap details...</p>
            </div>
        `);
        
        // Show the modal
        const modal = new bootstrap.Modal(document.getElementById('conceptmapModal'));
        modal.show();
        
        // Fetch ConceptMap details from API
        $.ajax({
            url: `/fhir/ConceptMap/${id}`,
            method: 'GET',
            dataType: 'json',
            timeout: AJAX_TIMEOUT_MS,
            success: function(data) {
                displayConceptMapDetails(data);
            },
            error: function(xhr, status, error) {
                let errorMessage = 'Error loading ConceptMap details';
                if (status === 'timeout') {
                    errorMessage = 'Request timed out. Please try again.';
                } else if (xhr.status === 404) {
                    errorMessage = 'ConceptMap not found.';
                } else if (xhr.status === 500) {
                    errorMessage = 'Server error. Please try again later.';
                } else if (error) {
                    errorMessage = `Error: ${error}`;
                }
                
                $('#conceptmapDetails').html(`
                    <div class="alert alert-danger">
                        <i class="bi bi-exclamation-triangle"></i>
                        ${errorMessage}
                    </div>
                `);
                
                console.error('Error loading ConceptMap details:', {xhr, status, error});
            }
        });
    };



    // Function to display CodeSystem details in modal
    function displayCodeSystemDetails(codeSystem) {
        const details = `
            <div class="row">
                <div class="col-md-6">
                    <h6 class="fw-bold">Basic Information</h6>
                    <table class="table table-sm">
                        <tbody>
                            <tr>
                                <td class="fw-bold">ID:</td>
                                <td>${codeSystem.id || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Title:</td>
                                <td>${codeSystem.title || codeSystem.name || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">URL:</td>
                                <td>${codeSystem.url || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Version:</td>
                                <td>${codeSystem.version || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Status:</td>
                                <td>
                                    <span class="badge ${codeSystem.status === 'active' ? 'bg-success' : 'bg-secondary'}">
                                        ${codeSystem.status || 'N/A'}
                                    </span>
                                </td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Publisher:</td>
                                <td>${codeSystem.publisher || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Date:</td>
                                <td>${codeSystem.date ? new Date(codeSystem.date).toLocaleDateString() : 'N/A'}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                <div class="col-md-6">
                    <h6 class="fw-bold">Technical Details</h6>
                    <table class="table table-sm">
                        <tbody>
                            <tr>
                                <td class="fw-bold">Hierarchy Meaning:</td>
                                <td>${codeSystem.hierarchyMeaning || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Compositional:</td>
                                <td>
                                    <span class="badge ${codeSystem.compositional ? 'bg-info' : 'bg-secondary'}">
                                        ${codeSystem.compositional ? 'Yes' : 'No'}
                                    </span>
                                </td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Content:</td>
                                <td>
                                    <span class="badge ${codeSystem.content === 'complete' ? 'bg-success' : 'bg-warning'}">
                                        ${codeSystem.content || 'N/A'}
                                    </span>
                                </td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Resource Type:</td>
                                <td>${codeSystem.resourceType || 'N/A'}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
            ${codeSystem.description ? `
            <div class="row mt-3">
                <div class="col-12">
                    <h6 class="fw-bold">Description</h6>
                    <p class="text-muted">${codeSystem.description}</p>
                </div>
            </div>
            ` : ''}
        `;
        
        $('#codesystemDetails').html(details);
    }

    // Function to display ValueSet details in modal
    function displayValueSetDetails(valueSet) {
        const details = `
            <div class="row">
                <div class="col-md-6">
                    <h6 class="fw-bold">Basic Information</h6>
                    <table class="table table-sm">
                        <tbody>
                            <tr>
                                <td class="fw-bold">ID:</td>
                                <td>${valueSet.id || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Name:</td>
                                <td>${valueSet.name || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Title:</td>
                                <td>${valueSet.title || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">URL:</td>
                                <td>${valueSet.url || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Version:</td>
                                <td>${valueSet.version || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Status:</td>
                                <td>
                                    <span class="badge ${valueSet.status === 'active' ? 'bg-success' : 'bg-secondary'}">
                                        ${valueSet.status || 'N/A'}
                                    </span>
                                </td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Publisher:</td>
                                <td>${valueSet.publisher || 'N/A'}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                <div class="col-md-6">
                    <h6 class="fw-bold">Technical Details</h6>
                    <table class="table table-sm">
                        <tbody>
                            <tr>
                                <td class="fw-bold">Resource Type:</td>
                                <td>${valueSet.resourceType || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Experimental:</td>
                                <td>
                                    <span class="badge ${valueSet.experimental ? 'bg-warning' : 'bg-info'}">
                                        ${valueSet.experimental ? 'Yes' : 'No'}
                                    </span>
                                </td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Date:</td>
                                <td>${valueSet.date ? new Date(valueSet.date).toLocaleDateString() : 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Description:</td>
                                <td>${valueSet.description || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Purpose:</td>
                                <td>${valueSet.purpose || 'N/A'}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
            ${valueSet.copyright ? `
            <div class="row mt-3">
                <div class="col-12">
                    <h6 class="fw-bold">Copyright</h6>
                    <div class="alert alert-info">
                        <small class="text-muted">${valueSet.copyright}</small>
                    </div>
                </div>
            </div>
            ` : ''}
        `;
        
        $('#valuesetDetails').html(details);
    }

    // Function to display ConceptMap details in modal
    function displayConceptMapDetails(conceptMap) {
        const details = `
            <div class="row">
                <div class="col-md-6">
                    <h6 class="fw-bold">Basic Information</h6>
                    <table class="table table-sm">
                        <tbody>
                            <tr>
                                <td class="fw-bold">ID:</td>
                                <td>${conceptMap.id || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Name:</td>
                                <td>${conceptMap.name || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Title:</td>
                                <td>${conceptMap.title || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">URL:</td>
                                <td>${conceptMap.url || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Version:</td>
                                <td>${conceptMap.version || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Status:</td>
                                <td>
                                    <span class="badge ${conceptMap.status === 'active' ? 'bg-success' : 'bg-secondary'}">
                                        ${conceptMap.status || 'N/A'}
                                    </span>
                                </td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Publisher:</td>
                                <td>${conceptMap.publisher || 'N/A'}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                <div class="col-md-6">
                    <h6 class="fw-bold">Mapping Details</h6>
                    <table class="table table-sm">
                        <tbody>
                            <tr>
                                <td class="fw-bold">Resource Type:</td>
                                <td>${conceptMap.resourceType || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Source URI:</td>
                                <td>${conceptMap.sourceUri || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Target URI:</td>
                                <td>${conceptMap.targetUri || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Source Code System:</td>
                                <td>${conceptMap.sourceCodeSystem || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Target Code System:</td>
                                <td>${conceptMap.targetCodeSystem || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="fw-bold">Experimental:</td>
                                <td>
                                    <span class="badge ${conceptMap.experimental ? 'bg-warning' : 'bg-info'}">
                                        ${conceptMap.experimental ? 'Yes' : 'No'}
                                    </span>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
            ${conceptMap.text && conceptMap.text.div ? `
            <div class="row mt-3">
                <div class="col-12">
                    <h6 class="fw-bold">Description</h6>
                    <div class="alert alert-info">
                        <div>${conceptMap.text.div}</div>
                        ${conceptMap.text.status ? `<small class="text-muted">Status: ${conceptMap.text.status}</small>` : ''}
                    </div>
                </div>
            </div>
            ` : ''}
            ${conceptMap.description ? `
            <div class="row mt-3">
                <div class="col-12">
                    <h6 class="fw-bold">Additional Description</h6>
                    <p class="text-muted">${conceptMap.description}</p>
                </div>
            </div>
            ` : ''}
        `;
        
        $('#conceptmapDetails').html(details);
    }
});
