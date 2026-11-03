package ru.netology.cloud.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import ru.netology.cloud.config.AppProperties;
import ru.netology.cloud.dto.FileInfo;
import ru.netology.cloud.exception.BadRequestException;
import ru.netology.cloud.model.StoredFile;
import ru.netology.cloud.model.UserAccount;
import ru.netology.cloud.repository.StoredFileRepository;
import ru.netology.cloud.repository.UserRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private StoredFileRepository fileRepository;
    @Mock
    private UserRepository userRepository;

    private FileService fileService;
    private Path storageDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        storageDir = tempDir;
        fileService = new FileService(fileRepository, userRepository,
                new AppProperties(tempDir.toString(), List.of(), List.of(),
                        java.time.Duration.ofHours(1), 5, java.time.Duration.ofMinutes(1)));
    }

    private UserAccount mockUser() {
        UserAccount user = org.mockito.Mockito.mock(UserAccount.class);
        org.mockito.Mockito.lenient().when(user.getId()).thenReturn(1L);
        when(userRepository.findByLogin("user")).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void uploadSavesMetadataAndContent() {
        mockUser();
        when(fileRepository.findByOwnerIdAndFilename(1L, "a.txt")).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        fileService.upload("user", "a.txt", null, file);

        ArgumentCaptor<StoredFile> captor = ArgumentCaptor.forClass(StoredFile.class);
        verify(fileRepository).saveAndFlush(captor.capture());
        assertEquals("a.txt", captor.getValue().getFilename());
        assertEquals(5, captor.getValue().getSize());
    }

    @Test
    void uploadDuplicateFails() {
        mockUser();
        when(fileRepository.findByOwnerIdAndFilename(1L, "a.txt"))
                .thenReturn(Optional.of(new StoredFile(1L, "a.txt", "x", 1, null)));
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        assertThrows(BadRequestException.class, () -> fileService.upload("user", "a.txt", null, file));
        verify(fileRepository, never()).saveAndFlush(any());
    }

    @Test
    void uploadRaceOnUniqueConstraintReturnsBadRequestAndCleansUp() throws Exception {
        mockUser();
        when(fileRepository.findByOwnerIdAndFilename(1L, "a.txt")).thenReturn(Optional.empty());
        when(fileRepository.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        assertThrows(BadRequestException.class, () -> fileService.upload("user", "a.txt", null, file));
        assertEquals(0, storedFileCount());
    }

    @Test
    void uploadRemovesContentWhenDatabaseFails() throws Exception {
        mockUser();
        when(fileRepository.findByOwnerIdAndFilename(1L, "a.txt")).thenReturn(Optional.empty());
        when(fileRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("db down"));
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        assertThrows(IllegalStateException.class, () -> fileService.upload("user", "a.txt", null, file));
        assertEquals(0, storedFileCount());
    }

    @Test
    void tooLongFilenameFails() {
        mockUser();
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        assertThrows(BadRequestException.class, () -> fileService.upload("user", "x".repeat(256), null, file));
    }

    private long storedFileCount() throws java.io.IOException {
        try (var files = java.nio.file.Files.list(storageDir)) {
            return files.count();
        }
    }

    @Test
    void uploadEmptyFileFails() {
        mockUser();
        MockMultipartFile empty = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        assertThrows(BadRequestException.class, () -> fileService.upload("user", "a.txt", null, empty));
    }

    @Test
    void downloadReturnsStoredBytes() throws java.io.IOException {
        mockUser();
        when(fileRepository.findByOwnerIdAndFilename(1L, "a.txt")).thenReturn(Optional.empty());
        fileService.upload("user", "a.txt", null,
                new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes()));
        ArgumentCaptor<StoredFile> captor = ArgumentCaptor.forClass(StoredFile.class);
        verify(fileRepository).saveAndFlush(captor.capture());
        when(fileRepository.findByOwnerIdAndFilename(1L, "a.txt")).thenReturn(Optional.of(captor.getValue()));

        assertArrayEquals("hello".getBytes(), java.nio.file.Files.readAllBytes(fileService.download("user", "a.txt")));
    }

    @Test
    void deleteUnknownFileFails() {
        mockUser();
        when(fileRepository.findByOwnerIdAndFilename(1L, "nope")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> fileService.delete("user", "nope"));
    }

    @Test
    void renameChangesFilename() {
        mockUser();
        StoredFile stored = new StoredFile(1L, "a.txt", "x", 1, null);
        when(fileRepository.findByOwnerIdAndFilename(1L, "a.txt")).thenReturn(Optional.of(stored));
        when(fileRepository.findByOwnerIdAndFilename(1L, "b.txt")).thenReturn(Optional.empty());

        fileService.rename("user", "a.txt", "b.txt");

        assertEquals("b.txt", stored.getFilename());
    }

    @Test
    void renameToExistingNameFails() {
        mockUser();
        when(fileRepository.findByOwnerIdAndFilename(1L, "a.txt"))
                .thenReturn(Optional.of(new StoredFile(1L, "a.txt", "x", 1, null)));
        when(fileRepository.findByOwnerIdAndFilename(1L, "b.txt"))
                .thenReturn(Optional.of(new StoredFile(1L, "b.txt", "y", 1, null)));

        assertThrows(BadRequestException.class, () -> fileService.rename("user", "a.txt", "b.txt"));
    }

    @Test
    void listMapsFilesAndAppliesLimit() {
        mockUser();
        when(fileRepository.findByOwnerIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(new StoredFile(1L, "a.txt", "x", 10, null)));

        List<FileInfo> result = fileService.list("user", 3);

        assertEquals(1, result.size());
        assertEquals("a.txt", result.get(0).filename());
        assertEquals(10, result.get(0).size());
        assertTrue(result.get(0).editedAt() > 0);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(fileRepository).findByOwnerIdOrderByCreatedAtDesc(any(), captor.capture());
        assertEquals(3, captor.getValue().getPageSize());
    }

    @Test
    void listWithNonPositiveLimitFails() {
        assertThrows(BadRequestException.class, () -> fileService.list("user", 0));
    }
}
