package org.magic.pyramidDraft.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.pyramidDraft.api.card.Cube;
import org.magic.pyramidDraft.external.CubeCobraService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CubeDownloaderTest {

    @Mock
    CubeCobraService cubeCobraService;

    CubeDownloader cubeDownloader;

    @BeforeEach
    void setUp() throws Exception {
        cubeDownloader = new CubeDownloader(cubeCobraService);
        setField("cubeOwner", "testOwner");
    }

    private void setField(String name, String value) throws Exception {
        Field field = CubeDownloader.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(cubeDownloader, value);
    }

    @Test
    void testDevProfileCallsGetByOwner() throws Exception {
        setField("activeProfile", "dev");
        Cube mockCube = mock(Cube.class);

        when(cubeCobraService.getCubeDataAsJsonByOwner(anyString()))
            .thenReturn(Uni.createFrom().item(List.of(mockCube)));

        Cube result = cubeDownloader.getCubeForCubeID("any-id")
            .await().atMost(Duration.ofSeconds(2));

        assertNotNull(result);
        verify(cubeCobraService).getCubeDataAsJsonByOwner("testOwner");
        verify(cubeCobraService, never()).getCubeDataAsJson(anyString());
    }

    @Test
    void testGappedProfileCallsGetByOwner() throws Exception {
        setField("activeProfile", "gapped");
        Cube mockCube = mock(Cube.class);

        when(cubeCobraService.getCubeDataAsJsonByOwner(anyString()))
            .thenReturn(Uni.createFrom().item(List.of(mockCube)));

        Cube result = cubeDownloader.getCubeForCubeID("any-id")
            .await().atMost(Duration.ofSeconds(2));

        assertNotNull(result);
        verify(cubeCobraService).getCubeDataAsJsonByOwner("testOwner");
        verify(cubeCobraService, never()).getCubeDataAsJson(anyString());
    }

    @Test
    void testProdProfileCallsGetById() throws Exception {
        setField("activeProfile", "prod");
        Cube mockCube = mock(Cube.class);

        when(cubeCobraService.getCubeDataAsJson(anyString()))
            .thenReturn(Uni.createFrom().item(mockCube));

        Cube result = cubeDownloader.getCubeForCubeID("prod-cube-id")
            .await().atMost(Duration.ofSeconds(2));

        assertNotNull(result);
        verify(cubeCobraService).getCubeDataAsJson("prod-cube-id");
        verify(cubeCobraService, never()).getCubeDataAsJsonByOwner(anyString());
    }

    @Test
    void testDefaultProfileThrows() throws Exception {
        setField("activeProfile", "unknown");

        assertThrows(Throwable.class, () -> {
            cubeDownloader.getCubeForCubeID("any-id")
                .await().atMost(Duration.ofSeconds(2));
        });
    }
}
