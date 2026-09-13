package com.tikitaka.question.controller;

import static com.tikitaka.question.dto.response.QuestionResponses.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.question.dto.request.*;
import com.tikitaka.question.service.QuestionService;
import com.tikitaka.question.service.QuestionService.QuestionScope;
import com.tikitaka.question.service.QuestionService.QuestionSortType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController @RequiredArgsConstructor
public class QuestionController {
 private final QuestionService service; private final CurrentUserResolver users;
 @GetMapping("/api/v1/spaces/{spaceId}/questions") public ListResponse list(@PathVariable UUID spaceId,@RequestParam(defaultValue="LATEST") QuestionSortType sort,@RequestParam(required=false,name="document_id") UUID documentId,@RequestParam(required=false,name="category_id") UUID categoryId,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int size,Authentication a){return service.list(spaceId,sort,documentId,categoryId,cursor,size,users.resolve(a),false);}
 @GetMapping("/api/v1/spaces/{spaceId}/questions/mine") public ListResponse mine(@PathVariable UUID spaceId,@RequestParam(defaultValue="LATEST") QuestionSortType sort,@RequestParam(required=false,name="document_id") UUID documentId,@RequestParam(required=false,name="category_id") UUID categoryId,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int size,Authentication a){return service.list(spaceId,sort,documentId,categoryId,cursor,size,users.resolve(a),true);}
 @GetMapping("/api/v1/spaces/{spaceId}/questions/my-summary") public Summary summary(@PathVariable UUID spaceId,Authentication a){return service.summary(spaceId,users.resolve(a));}
 @GetMapping("/api/v1/documents/{documentId}/questions") public DocumentListResponse document(@PathVariable UUID documentId,@RequestParam(defaultValue="ALL") QuestionScope scope,@RequestParam(required=false,name="slide_id") UUID slideId,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int size,Authentication a){return service.documentQuestions(documentId,scope,slideId,cursor,size,users.resolve(a));}
 @GetMapping("/api/v1/questions/{id}") public Detail detail(@PathVariable UUID id,Authentication a){return service.detail(id,users.resolve(a));}
 @PostMapping("/api/v1/slides/{slideId}/questions") public Create createPinned(@PathVariable UUID slideId,@Valid @RequestBody QuestionCreateRequest r,Authentication a){return service.createPinned(slideId,r,users.resolve(a));}
 @PostMapping("/api/v1/spaces/{spaceId}/questions") public SpaceCreate create(@PathVariable UUID spaceId,@Valid @RequestBody SpaceQuestionCreateRequest r,Authentication a){return service.create(spaceId,r,users.resolve(a));}
 @PostMapping("/api/v1/spaces/{spaceId}/questions/similar") public SimilarResponse similar(@PathVariable UUID spaceId,@Valid @RequestBody SimilarQuestionRequest r,Authentication a){return service.similar(spaceId,r,users.resolve(a));}
 @DeleteMapping("/api/v1/questions/{id}") public Delete delete(@PathVariable UUID id,Authentication a){return service.deleteQuestion(id,users.resolve(a));}
 @PostMapping("/api/v1/questions/{id}/answers") public AnswerMutation answer(@PathVariable UUID id,@Valid @RequestBody ContentRequest r,Authentication a){return service.addAnswer(id,r.content(),users.resolve(a));}
 @PatchMapping("/api/v1/answers/{id}") public AnswerMutation updateAnswer(@PathVariable UUID id,@Valid @RequestBody ContentRequest r,Authentication a){return service.updateAnswer(id,r.content(),users.resolve(a));}
 @DeleteMapping("/api/v1/answers/{id}") public AnswerMutation deleteAnswer(@PathVariable UUID id,Authentication a){return service.deleteAnswer(id,users.resolve(a));}
 @PostMapping("/api/v1/questions/{id}/comments") public CommentMutation comment(@PathVariable UUID id,@Valid @RequestBody CommentCreateRequest r,Authentication a){return service.addComment(id,r,users.resolve(a));}
 @PatchMapping("/api/v1/question-comments/{id}") public CommentMutation updateComment(@PathVariable UUID id,@Valid @RequestBody ContentRequest r,Authentication a){return service.updateComment(id,r.content(),users.resolve(a));}
 @DeleteMapping("/api/v1/question-comments/{id}") public CommentMutation deleteComment(@PathVariable UUID id,Authentication a){return service.deleteComment(id,users.resolve(a));}
 @PostMapping("/api/v1/questions/{id}/likes") public Like like(@PathVariable UUID id,Authentication a){return service.like(id,users.resolve(a));}
 @DeleteMapping("/api/v1/questions/{id}/likes") public Like unlike(@PathVariable UUID id,Authentication a){return service.unlike(id,users.resolve(a));}
 @GetMapping("/api/v1/spaces/{spaceId}/question-categories") public CategoriesResponse categories(@PathVariable UUID spaceId,Authentication a){return service.categoryList(spaceId,users.resolve(a));}
 @PatchMapping("/api/v1/spaces/{spaceId}/question-categories") public CategoryBatchResponse saveCategories(@PathVariable UUID spaceId,@Valid @RequestBody CategoryBatchRequest r,Authentication a){return service.saveCategories(spaceId,r,users.resolve(a));}
 @GetMapping("/api/v1/spaces/{spaceId}/questions/export") public ExportResponse export(@PathVariable UUID spaceId,@RequestParam(defaultValue="csv") String format,Authentication a){return service.export(spaceId,format,users.resolve(a));}
}
