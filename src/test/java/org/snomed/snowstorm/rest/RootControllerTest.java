package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;

import java.net.URI;
import java.net.URISyntaxException;

public class RootControllerTest extends AbstractControllerSecurityTest{

    @Test
    void getRoot() throws URISyntaxException {
        RequestEntity<Object> request = new RequestEntity<>(HttpMethod.GET, new URI(url));
        testStatusCode(HttpStatus.FOUND, userWithoutRoleHeaders, request);
    }
}
