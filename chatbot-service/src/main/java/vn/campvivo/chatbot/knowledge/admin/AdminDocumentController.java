package vn.campvivo.chatbot.knowledge.admin;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.campvivo.chatbot.common.dto.ApiResponse;
import vn.campvivo.chatbot.common.dto.PageQuery;
import vn.campvivo.chatbot.common.dto.PageResponse;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.DocumentFile;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.DocumentStatus;
import vn.campvivo.chatbot.knowledge.admin.DocumentDtos.DocumentDetailResponse;
import vn.campvivo.chatbot.knowledge.admin.DocumentDtos.DocumentResponse;
import vn.campvivo.chatbot.knowledge.admin.DocumentDtos.RetrievalHitResponse;
import vn.campvivo.chatbot.knowledge.admin.DocumentDtos.SearchTestRequest;
import vn.campvivo.chatbot.knowledge.admin.DocumentDtos.UpdateDocumentRequest;
import vn.campvivo.chatbot.security.CurrentUserId;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Admin quản lý tài liệu cho chatbot — api.md 4.12. Quyền ADMIN (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/chatbot/documents")
public class AdminDocumentController {

    private final AdminDocumentService service;

    public AdminDocumentController(AdminDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<DocumentResponse> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) DocumentStatus status,
                                               @RequestParam(required = false) Boolean isActive,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer limit) {
        return service.list(q, status, isActive, PageQuery.of(page, limit));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentResponse> create(@RequestPart(value = "file", required = false) MultipartFile file,
                                                @RequestParam(required = false) String title,
                                                @RequestParam(required = false) String description,
                                                @CurrentUserId Long adminId) {
        return ApiResponse.of(service.create(file, title, description, adminId));
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentDetailResponse> get(@PathVariable long id) {
        return ApiResponse.of(service.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<DocumentResponse> update(@PathVariable long id,
                                                @Valid @RequestBody UpdateDocumentRequest request) {
        return ApiResponse.of(service.update(id, request));
    }

    @PutMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentResponse> replaceFile(@PathVariable long id,
                                                     @RequestPart(value = "file", required = false) MultipartFile file) {
        return ApiResponse.of(service.replaceFile(id, file));
    }

    @PostMapping("/{id}/reindex")
    public ApiResponse<DocumentResponse> reindex(@PathVariable long id) {
        return ApiResponse.of(service.reindex(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable long id) {
        DocumentFile file = service.file(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    @PostMapping("/search-test")
    public ApiResponse<List<RetrievalHitResponse>> searchTest(@Valid @RequestBody SearchTestRequest request) {
        return ApiResponse.of(service.searchTest(request.question()));
    }
}
