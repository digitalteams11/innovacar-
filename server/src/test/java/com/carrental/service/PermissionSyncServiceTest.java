package com.carrental.service;

import com.carrental.entity.PermissionDefinition;
import com.carrental.entity.PermissionRiskLevel;
import com.carrental.repository.PermissionDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionSyncServiceTest {

    @Mock private PermissionDefinitionRepository definitionRepository;

    @InjectMocks
    private PermissionSyncService permissionSyncService;

    @Test
    void sync_insertsEveryCatalogCodeNotYetInTheDatabase() {
        when(definitionRepository.findAll()).thenReturn(List.of());
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PermissionSyncService.SyncResult result = permissionSyncService.sync();

        assertThat(result.added()).isEqualTo(PermissionCatalog.ENTRIES.size());
        assertThat(result.deactivated()).isZero();
        verify(definitionRepository, times(PermissionCatalog.ENTRIES.size())).save(any());
    }

    @Test
    void sync_preservesAnExistingRowsGrantsAndOnlyUpdatesMetadata() {
        PermissionDefinition existing = PermissionDefinition.builder()
                .id(1L).code("VEHICLE_VIEW").name("VEHICLE_VIEW").category("OLD_CATEGORY")
                .module("OLD_MODULE").active(true).deprecated(false).sortOrder(0).build();
        when(definitionRepository.findAll()).thenReturn(List.of(existing));
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PermissionSyncService.SyncResult result = permissionSyncService.sync();

        assertThat(existing.getModule()).isEqualTo("FLEET");
        assertThat(existing.getActive()).isTrue();
        assertThat(result.added()).isEqualTo(PermissionCatalog.ENTRIES.size() - 1);
        assertThat(result.deactivated()).isZero();
    }

    @Test
    void sync_deactivatesARowWhoseCodeNoLongerExistsInTheCatalog_butNeverDeletesIt() {
        PermissionDefinition removed = PermissionDefinition.builder()
                .id(99L).code("SOME_REMOVED_FEATURE_CODE").name("x").category("x")
                .active(true).deprecated(false).sortOrder(0).build();
        when(definitionRepository.findAll()).thenReturn(List.of(removed));
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PermissionSyncService.SyncResult result = permissionSyncService.sync();

        assertThat(removed.getActive()).isFalse();
        assertThat(result.deactivated()).isEqualTo(1);
        verify(definitionRepository, never()).delete(any());
        verify(definitionRepository, never()).deleteById(any());
    }

    @Test
    void sync_isANoOpAfterTheFirstSuccessfulRunInThisProcess() {
        when(definitionRepository.findAll()).thenReturn(List.of());
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        permissionSyncService.sync();
        reset(definitionRepository);

        PermissionSyncService.SyncResult second = permissionSyncService.sync();

        assertThat(second.added()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(second.deactivated()).isZero();
        verifyNoInteractions(definitionRepository);
    }

    @Test
    void isDangerous_trueOnlyForDangerousRiskLevel() {
        assertThat(PermissionCatalog.isDangerous(PermissionRiskLevel.DANGEROUS)).isTrue();
        assertThat(PermissionCatalog.isDangerous(PermissionRiskLevel.ELEVATED)).isFalse();
        assertThat(PermissionCatalog.isDangerous(PermissionRiskLevel.NORMAL)).isFalse();
    }
}
