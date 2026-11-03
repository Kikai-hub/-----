package ru.netology.cloud.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.netology.cloud.config.AppProperties;
import ru.netology.cloud.dto.FileInfo;
import ru.netology.cloud.exception.BadRequestException;
import ru.netology.cloud.model.StoredFile;
import ru.netology.cloud.model.UserAccount;
import ru.netology.cloud.repository.StoredFileRepository;
import ru.netology.cloud.repository.UserRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class FileService {

    private static final int MAX_NAME_LENGTH = 255;

    private final StoredFileRepository fileRepository;
    private final UserRepository userRepository;
    private final Path root;

    public FileService(StoredFileRepository fileRepository, UserRepository userRepository, AppProperties properties) {
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.root = Path.of(properties.storageDir()).toAbsolutePath().normalize();
    }

    @Transactional
    public void upload(String login, String filename, String hash, MultipartFile file) {
        UserAccount user = user(login);
        validateName(filename);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (fileRepository.findByOwnerIdAndFilename(user.getId(), filename).isPresent()) {
            throw new BadRequestException("File already exists: " + filename);
        }
        String storageName = UUID.randomUUID().toString();
        Path target = root.resolve(storageName);
        try {
            Files.createDirectories(root);
            file.transferTo(target);
            fileRepository.saveAndFlush(new StoredFile(user.getId(), filename, storageName, file.getSize(), hash));
        } catch (DataIntegrityViolationException e) {
            deleteQuietly(target);
            throw new BadRequestException("File already exists: " + filename);
        } catch (IOException e) {
            deleteQuietly(target);
            throw new UncheckedIOException("Cannot store file", e);
        } catch (RuntimeException e) {
            deleteQuietly(target);
            throw e;
        }
    }

    /** Путь к содержимому файла на диске; читается потоком, а не целиком в память. */
    @Transactional(readOnly = true)
    public Path download(String login, String filename) {
        Path path = root.resolve(find(login, filename).getStorageName());
        if (!Files.isRegularFile(path)) {
            throw new BadRequestException("File content is missing: " + filename);
        }
        return path;
    }

    @Transactional
    public void delete(String login, String filename) {
        StoredFile stored = find(login, filename);
        fileRepository.delete(stored);
        try {
            Files.deleteIfExists(root.resolve(stored.getStorageName()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete file", e);
        }
    }

    @Transactional
    public void rename(String login, String filename, String newName) {
        validateName(newName);
        StoredFile stored = find(login, filename);
        if (!filename.equals(newName)
                && fileRepository.findByOwnerIdAndFilename(stored.getOwnerId(), newName).isPresent()) {
            throw new BadRequestException("File already exists: " + newName);
        }
        stored.setFilename(newName);
    }

    @Transactional(readOnly = true)
    public List<FileInfo> list(String login, Integer limit) {
        if (limit != null && limit <= 0) {
            throw new BadRequestException("Limit must be positive");
        }
        UserAccount user = user(login);
        int size = limit == null ? Integer.MAX_VALUE : limit;
        return fileRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, size)).stream()
                .map(f -> new FileInfo(f.getFilename(), f.getSize(), f.getEditedAt().toEpochMilli()))
                .toList();
    }

    private StoredFile find(String login, String filename) {
        validateName(filename);
        return fileRepository.findByOwnerIdAndFilename(user(login).getId(), filename)
                .orElseThrow(() -> new BadRequestException("File not found: " + filename));
    }

    private UserAccount user(String login) {
        return userRepository.findByLogin(login)
                .orElseThrow(() -> new BadRequestException("Unknown user"));
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Filename is required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("Filename is too long");
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // файл-сирота не критичен, исходная ошибка важнее
        }
    }
}
