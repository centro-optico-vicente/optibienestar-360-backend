package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.common.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneratedReportPurgeServiceTest {

    private static final Instant CUTOFF = Instant.parse("2026-10-15T00:00:00Z");

    @Mock private NamedParameterJdbcTemplate jdbc;
    @Mock private ObjectProvider<StorageService> storageProvider;
    @Mock private StorageService storage;

    private GeneratedReportPurgeService service() {
        return new GeneratedReportPurgeService(jdbc, storageProvider);
    }

    /** Builds rows through the service's own RowMapper, so the mapping is exercised too. */
    @SuppressWarnings("unchecked")
    private void batchReturns(Object[]... rows) throws SQLException {
        when(jdbc.query(contains("FROM attached_files"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<Object> mapper = invocation.getArgument(2);
                    List<Object> mapped = new ArrayList<>();
                    for (int i = 0; i < rows.length; i++) {
                        ResultSet rs = mock(ResultSet.class);
                        when(rs.getLong("attached_files_id")).thenReturn((Long) rows[i][0]);
                        when(rs.getObject("owner_uuid", UUID.class)).thenReturn(UUID.randomUUID());
                        when(rs.getString("file_key")).thenReturn((String) rows[i][1]);
                        when(rs.getTimestamp("uploaded_at")).thenReturn(Timestamp.from(CUTOFF.minusSeconds(3600 - i)));
                        mapped.add(mapper.mapRow(rs, i));
                    }
                    return mapped;
                });
    }

    @Test
    void deletesTheObjectsAndMarksOnlyTheRowsR2ConfirmedDeleted() throws SQLException {
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(storage.getBucket()).thenReturn("private");
        batchReturns(new Object[]{1L, "temporary/report_audit_log/a/x.pdf"},
                new Object[]{2L, "temporary/report_audit_log/b/y.pdf"});
        when(storage.deleteAll(eq("private"), any())).thenReturn(Set.of("temporary/report_audit_log/b/y.pdf"));

        var result = service().purgeUploadedBefore(CUTOFF);

        assertThat(result.purged()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        ArgumentCaptor<MapSqlParameterSource> marked = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(contains("UPDATE attached_files"), marked.capture());
        assertThat(marked.getValue().getValue("ids")).isEqualTo(List.of(1L));
        verify(jdbc).update(contains("UPDATE file_download_links"), any(MapSqlParameterSource.class));
    }

    @Test
    void aFailedDeleteRequestMarksNothingAndKeepsTheFilesForTheNextRun() throws SQLException {
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(storage.getBucket()).thenReturn("private");
        batchReturns(new Object[]{1L, "temporary/report_audit_log/a/x.pdf"});
        when(storage.deleteAll(anyString(), any())).thenThrow(new RuntimeException("R2 down"));

        var result = service().purgeUploadedBefore(CUTOFF);

        assertThat(result.purged()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void withoutStorageNothingIsTouched() {
        when(storageProvider.getIfAvailable()).thenReturn(null);

        var result = service().purgeUploadedBefore(CUTOFF);

        assertThat(result.storageAvailable()).isFalse();
        verifyNoInteractions(jdbc);
    }
}
