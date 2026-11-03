package ru.netology.cloud.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Метаданные файла. Само содержимое лежит на диске (storage-dir), в БД только ссылка на него.
 */
@Entity
@Table(name = "files", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "filename"}))
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "storage_name", nullable = false)
    private String storageName;

    @Column(nullable = false)
    private long size;

    private String hash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Nullable, чтобы ddl-auto=update мог добавить колонку в уже заполненную таблицу. */
    @Column(name = "edited_at")
    private Instant editedAt;

    protected StoredFile() {
    }

    public StoredFile(Long ownerId, String filename, String storageName, long size, String hash) {
        this.ownerId = ownerId;
        this.filename = filename;
        this.storageName = storageName;
        this.size = size;
        this.hash = hash;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
        this.editedAt = Instant.now();
    }

    public Instant getEditedAt() {
        return editedAt != null ? editedAt : createdAt;
    }

    public String getStorageName() {
        return storageName;
    }

    public long getSize() {
        return size;
    }

    public String getHash() {
        return hash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
