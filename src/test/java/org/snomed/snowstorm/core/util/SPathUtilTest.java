package org.snomed.snowstorm.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SPathUtilTest {
    @Test
    public void getAncestors_ShouldReturnExpected_WhenGivenInvalidUnexpectedFormat() {
        // then
        assertThrows(IllegalArgumentException.class, () -> SPathUtil.getAncestors(null));
        assertThrows(IllegalArgumentException.class, () -> SPathUtil.getAncestors("a/b/c/d/efg/h-i-j-k"));
        assertThrows(IllegalArgumentException.class, () -> SPathUtil.getAncestors("MAI/SNOMEDCT"));
        assertThrows(IllegalArgumentException.class, () -> SPathUtil.getAncestors("INVALID/SNOMEDCT-WRONG"));
    }

    @Test
    public void getAncestors_ShouldReturnExpected_WhenGivenInternationalCodeSystem() {
        // when
        List<String> result = SPathUtil.getAncestors("MAIN");

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    public void getAncestors_ShouldReturnExpected_WhenGivenInternationalProject() {
        // when
        List<String> result = SPathUtil.getAncestors("MAIN/projectA");

        // then
        assertEquals(1, result.size());
        assertEquals("MAIN", result.get(0));
    }

    @Test
    public void getAncestors_ShouldReturnExpected_WhenGivenInternationalTask() {
        // when
        List<String> result = SPathUtil.getAncestors("MAIN/projectA/taskB");

        // then
        assertEquals(2, result.size());
        assertEquals("MAIN/projectA", result.get(0));
        assertEquals("MAIN", result.get(1));
    }

    @Test
    public void getAncestors_ShouldReturnExpected_WhenGivenExtensionCodeSystem() {
        // when
        List<String> result = SPathUtil.getAncestors("MAIN/SNOMEDCT-XX");

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    public void getAncestors_ShouldReturnExpected_WhenGivenExtensionProject() {
        // when
        List<String> result = SPathUtil.getAncestors("MAIN/SNOMEDCT-XX/projectA");

        // then
        assertEquals(1, result.size());
        assertEquals("MAIN/SNOMEDCT-XX", result.get(0));
    }

    @Test
    public void getAncestors_ShouldReturnExpected_WhenGivenExtensionTask() {
        // when
        List<String> result = SPathUtil.getAncestors("MAIN/SNOMEDCT-XX/projectA/taskB");

        // then
        assertEquals(2, result.size());
        assertEquals("MAIN/SNOMEDCT-XX/projectA", result.get(0));
        assertEquals("MAIN/SNOMEDCT-XX", result.get(1));
    }
}