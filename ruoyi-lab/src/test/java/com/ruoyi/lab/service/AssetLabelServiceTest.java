package com.ruoyi.lab.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.vo.DeviceVo;

class AssetLabelServiceTest
{
    private final DeviceService devices = mock(DeviceService.class);
    private final AssetLabelService labels = new AssetLabelService(devices);

    @Test void readsAuthorizedDeviceAndReturnsMetadataOnly()
    {
        LabDevice device = new LabDevice();
        device.setId(9007199254740993L);
        device.setAssetNo("LAB-001");
        device.setName("显微镜");
        when(devices.getById(device.getId())).thenReturn(DeviceVo.from(device));
        var result = labels.labels(List.of("9007199254740993"));
        assertEquals("9007199254740993", result.get(0).id());
        assertEquals("LAB-001", result.get(0).assetNo());
        assertEquals("显微镜", result.get(0).name());
        verify(devices).getById(device.getId());
    }

    @Test void rejectsInvalidBatchBeforeReadingDevices()
    {
        for (List<String> input : List.of(List.<String>of(), List.of("1", "1"), List.of("01"),
                List.of("0"), List.of("9223372036854775808"), java.util.Collections.nCopies(101, "1")))
        {
            assertThrows(RuntimeException.class, () -> labels.labels(input));
        }
        assertThrows(RuntimeException.class, () -> labels.labels(null));
        verifyNoInteractions(devices);
    }

    @Test void doesNotReturnPartialLabelsOnAccessDenied()
    {
        when(devices.getById(1L)).thenThrow(new IllegalStateException("denied"));
        assertThrows(IllegalStateException.class, () -> labels.labels(List.of("1", "2")));
        verify(devices, never()).getById(2L);
    }
}
