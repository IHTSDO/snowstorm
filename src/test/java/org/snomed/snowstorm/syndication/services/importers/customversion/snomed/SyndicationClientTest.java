package org.snomed.snowstorm.syndication.services.importers.customversion.snomed;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.models.integration.SyndicationFeed;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class SyndicationClientTest {

    public static final String EXTENSION_URI = "http://snomed.info/sct/11000172109/version/20250315";
    public static final String EDITION_URI = "http://snomed.info/sct/900000000000207008/version/20250301";
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private SnomedSyndicationClient syndicationClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(syndicationClient, "restTemplate", restTemplate);
    }

    @Test
    void testGetFeed_Success() {
        doReturn(new ResponseEntity<>(getFeedResponse(), HttpStatus.OK))
                .when(restTemplate).exchange(eq("/feed"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

        SyndicationFeed syndicationFeed = ReflectionTestUtils.invokeMethod(syndicationClient, "getFeed");
        verify(restTemplate).exchange(eq("/feed"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        assert syndicationFeed != null;
        assertEquals(2, syndicationFeed.getEntries().size());
    }

    @Test
    void testDownloadPackages_ThrowsException_WhenNoEntries() {
        doReturn(new ResponseEntity<>(getEmptyFeedResponse(), HttpStatus.OK))
                .when(restTemplate).exchange(eq("/feed"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

        ServiceException exception = assertThrows(ServiceException.class, () ->
                syndicationClient.downloadPackages("someUri", "user", "pass"));
        assertTrue(exception.getMessage().contains("No matching syndication entry was found for URI"));
    }

    @Test
    void testDownloadEdition_downloads_one_package() throws ServiceException, IOException {
        doReturn(new ResponseEntity<>(getFeedResponse(), HttpStatus.OK))
                .when(restTemplate).exchange(eq("/feed"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

        List<File> downloadedPackagePaths = syndicationClient.downloadPackages(EDITION_URI, "user", "pass");
        Assertions.assertEquals(1, downloadedPackagePaths.size());
    }

    @Test
    void testDownloadExtension_downloads_2_packages() throws ServiceException, IOException {
        doReturn(new ResponseEntity<>(getFeedResponse(), HttpStatus.OK))
                .when(restTemplate).exchange(eq("/feed"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

        List<File> downloadedPackagePaths = syndicationClient.downloadPackages(EXTENSION_URI, "user", "pass");
        Assertions.assertEquals(2, downloadedPackagePaths.size());
    }

    private static @NotNull String getFeedResponse() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:ncts="http://ns.electronichealth.net.au/ncts/syndication/asf/extensions/1.0.0" xmlns:sct="http://snomed.info/syndication/sct-extension/1.0.0">
                    <title>SNOMED International MLDS Terminology Release Syndication Feed</title>
                    <link rel="alternate" type="application/atom+xml" href="http://ns.electronichealth.net.au/ncts/syndication/asf/profile/1.0.0" />
                    <id>urn:uuid:5c26c3ca-c21c-476e-b0a4-096679529f2e</id>
                    <generator>SNOMED International</generator>
                    <updated>2025-03-19T10:01:32Z</updated>
                    <ncts:atomSyndicationFormatProfile>http://ns.electronichealth.net.au/ncts/syndication/asf/profile/1.0.0</ncts:atomSyndicationFormatProfile>
                    <entry>
                        <title>Belgian National Edition-March 2025 v1.0</title>
                        <link rel="alternate" type="application/zip" href="https://mlds.ihtsdotools.org/api/releasePackages/190440/releaseVersions/1433217/releaseFiles/1433220/download" length="690104036" sct:md5Hash="69319deb0bdb31bd8e72ee057df5b9a5" />
                        <link rel="related" type="application/pdf" href="https://mlds.ihtsdotools.org/api/releasePackages/190440/releaseVersions/1433217/releaseFiles/1433450/download" length="108550" sct:md5Hash="1c2e9c571cd27e2518d2b62aa218346d" />
                        <category term="SCT_RF2_ALL" label="SNOMED CT RF2 All" scheme="http://ns.electronichealth.net.au/ncts/syndication/asf/scheme/1.0.0" />
                        <author>
                            <name>null</name>
                            <uri>null</uri>
                            <email>null</email>
                        </author>
                        <id>urn:uuid:a900c363-4ee0-45ee-8e28-827eef515399</id>
                        <rights>© International Health Terminology Standards Development Organisation 2002-2023.  All rights reserved.  SNOMED CT® was originally created by the College of American Pathologists.  "SNOMED" and "SNOMED CT" are registered trademarks of International Health Terminology Standards Development Organisation, trading as SNOMED International.</rights>
                        <updated>2025-03-14T14:04:39Z</updated>
                        <published>2025-03-15T00:00:00Z</published>
                        <summary>March 2025 SNOMED CT Managed Service Belgium Edition release</summary>
                        <ncts:contentItemIdentifier>http://snomed.info/sct/11000172109</ncts:contentItemIdentifier>
                        <ncts:contentItemVersion>http://snomed.info/sct/11000172109/version/20250315</ncts:contentItemVersion>
                        <sct:packageDependency>
                            <sct:editionDependency>http://snomed.info/sct/900000000000207008/version/20250301</sct:editionDependency>
                        </sct:packageDependency>
                    </entry>\
                   <entry>
                        <title>SNOMED CT International Edition-March 2025 v1.0</title>
                        <link rel="alternate" type="application/zip" href="https://mlds.ihtsdotools.org/api/releasePackages/167/releaseVersions/1430340/releaseFiles/1430353/download" length="562122950" sct:md5Hash="4faae1126b7bc1c3b808df5c5b38c48a" />
                        <link rel="related" type="application/pdf" href="https://mlds.ihtsdotools.org/api/releasePackages/167/releaseVersions/1430340/releaseFiles/1430357/download" length="92483" sct:md5Hash="9722970993c7a578ef02ce2833a9232b" />
                        <category term="SCT_RF2_ALL" label="SNOMED CT RF2 All" scheme="http://ns.electronichealth.net.au/ncts/syndication/asf/scheme/1.0.0" />
                        <author>
                            <name>SNOMED International</name>
                            <uri>https://www.snomed.org</uri>
                            <email>info@snomed.org</email>
                        </author>
                        <id>urn:uuid:efd47ea7-3597-4beb-bae1-d2002d7b43b3</id>
                        <rights>© International Health Terminology Standards Development Organisation 2002-2024.  All rights reserved.  SNOMED CT® was originally created by the College of American Pathologists.  "SNOMED" and "SNOMED CT" are registered trademarks of International Health Terminology Standards Development Organisation, trading as SNOMED International.</rights>
                        <updated>2025-02-28T12:19:44Z</updated>
                        <published>2025-03-01T00:00:00Z</published>
                        <summary>March 2025 SNOMED CT International Edition release package</summary>
                        <ncts:contentItemIdentifier>http://snomed.info/sct/900000000000207008</ncts:contentItemIdentifier>
                        <ncts:contentItemVersion>http://snomed.info/sct/900000000000207008/version/20250301</ncts:contentItemVersion>
                    </entry>\
                </feed>""";
    }

    private static @NotNull String getEmptyFeedResponse() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:ncts="http://ns.electronichealth.net.au/ncts/syndication/asf/extensions/1.0.0" xmlns:sct="http://snomed.info/syndication/sct-extension/1.0.0">
                </feed>""";
    }
}
