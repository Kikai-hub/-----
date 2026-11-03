package ru.netology.cloud.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.netology.cloud.model.StoredFile;

import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByOwnerIdAndFilename(Long ownerId, String filename);

    List<StoredFile> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);
}
