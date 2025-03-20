package org.snomed.snowstorm.core.util;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.ConceptMini;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamUtilTest {

    @Test
    void testMergeFunction_throwsExceptionOnDuplicateKey() {
        ConceptMini concept1 = mock(ConceptMini.class);
        ConceptMini concept2 = mock(ConceptMini.class);

        when(concept1.getConceptId()).thenReturn("12345");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                StreamUtil.MERGE_FUNCTION.apply(concept1, concept2));

        assertEquals("Duplicate key 12345", exception.getMessage());
    }

    @Test
    void testCopyWithProgress_copiesDataCorrectly() throws IOException {
        String testData = "This is a test string for InputStream.";
        InputStream inputStream = new ByteArrayInputStream(testData.getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        ByteArrayOutputStream consoleOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(consoleOut));

        StreamUtil.copyWithProgress(inputStream, outputStream, testData.length(), "Progress: %d%%");
        System.setOut(originalOut);

        assertEquals(testData, outputStream.toString(StandardCharsets.UTF_8));
        String consoleOutput = consoleOut.toString();
        assertTrue(consoleOutput.contains("Progress: 0%"));
    }
}
