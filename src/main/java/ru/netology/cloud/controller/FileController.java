package ru.netology.cloud.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.netology.cloud.dto.FileInfo;
import ru.netology.cloud.dto.RenameRequest;
import ru.netology.cloud.security.AuthTokenFilter;
import ru.netology.cloud.service.FileService;

import java.util.List;

@RestController
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void upload(@RequestAttribute(AuthTokenFilter.LOGIN_ATTRIBUTE) String login,
                       @RequestParam("filename") String filename,
                       @RequestParam(value = "hash", required = false) String hash,
                       @RequestPart("file") MultipartFile file) {
        fileService.upload(login, filename, hash, file);
    }

    @GetMapping("/file")
    public ResponseEntity<Resource> download(@RequestAttribute(AuthTokenFilter.LOGIN_ATTRIBUTE) String login,
                                             @RequestParam("filename") String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(fileService.download(login, filename)));
    }

    @DeleteMapping("/file")
    public void delete(@RequestAttribute(AuthTokenFilter.LOGIN_ATTRIBUTE) String login,
                       @RequestParam("filename") String filename) {
        fileService.delete(login, filename);
    }

    @PutMapping("/file")
    public void rename(@RequestAttribute(AuthTokenFilter.LOGIN_ATTRIBUTE) String login,
                       @RequestParam("filename") String filename,
                       @RequestBody RenameRequest request) {
        fileService.rename(login, filename, request.newName());
    }

    @GetMapping("/list")
    public List<FileInfo> list(@RequestAttribute(AuthTokenFilter.LOGIN_ATTRIBUTE) String login,
                               @RequestParam(value = "limit", required = false) Integer limit) {
        return fileService.list(login, limit);
    }
}
