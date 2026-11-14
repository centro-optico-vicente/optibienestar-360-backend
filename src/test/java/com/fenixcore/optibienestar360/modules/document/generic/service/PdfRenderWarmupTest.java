package com.fenixcore.optibienestar360.modules.document.generic.service;

import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericRecordModel;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PdfRenderWarmupTest {

    @Test
    void warmUpRendersTheRecordCardTemplate() {
        GenericHtmlPdfService htmlPdfService = mock(GenericHtmlPdfService.class);
        when(htmlPdfService.generatePdf(any(), any(), any())).thenReturn(new byte[]{1, 2, 3});

        new PdfRenderWarmup(htmlPdfService).warmUp();

        verify(htmlPdfService).generatePdf(any(GenericRecordModel.class), eq("documents/generic_record_card"),
                eq(Locale.forLanguageTag("es")));
    }

    @Test
    void warmUpFailureIsSwallowed() {
        GenericHtmlPdfService htmlPdfService = mock(GenericHtmlPdfService.class);
        when(htmlPdfService.generatePdf(any(), any(), any())).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> new PdfRenderWarmup(htmlPdfService).warmUp());
    }

    @Test
    void sampleModelExercisesFieldsAndADetailTable() {
        GenericRecordModel model = PdfRenderWarmup.sampleModel();

        assertFalse(model.fields().isEmpty());
        assertEquals(1, model.detailSections().size());
        assertFalse(model.detailSections().get(0).rows().isEmpty());
    }
}
