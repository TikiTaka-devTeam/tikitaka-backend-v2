package com.tikitaka.question.service;

import static com.tikitaka.question.dto.response.QuestionResponses.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.document.entity.Document;
import com.tikitaka.document.entity.Slide;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.global.common.cursor.CursorCodec;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.question.dto.request.CategoryBatchRequest;
import com.tikitaka.question.dto.request.CommentCreateRequest;
import com.tikitaka.question.dto.request.QuestionCreateRequest;
import com.tikitaka.question.dto.request.SimilarQuestionRequest;
import com.tikitaka.question.dto.request.SpaceQuestionCreateRequest;
import com.tikitaka.question.entity.Answer;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.entity.QuestionComment;
import com.tikitaka.question.entity.QuestionLike;
import com.tikitaka.question.entity.QuestionStatus;
import com.tikitaka.question.exception.QuestionErrorCode;
import com.tikitaka.question.repository.AnswerRepository;
import com.tikitaka.question.repository.QuestionCategoryMappingRepository;
import com.tikitaka.question.repository.QuestionCategoryRepository;
import com.tikitaka.question.repository.QuestionCommentRepository;
import com.tikitaka.question.repository.QuestionLikeRepository;
import com.tikitaka.question.repository.QuestionRepository;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionService {
    private static final int DEFAULT_SIZE=20, MAX_SIZE=100;
    private final QuestionRepository questions; private final AnswerRepository answers;
    private final QuestionCommentRepository comments; private final QuestionLikeRepository likes;
    private final QuestionCategoryRepository categories; private final QuestionCategoryMappingRepository mappings;
    private final DocumentRepository documents; private final SlideRepository slides;
    private final SpaceMemberRepository members; private final SpaceMemberPermissionRepository permissions;
    private final CursorCodec cursorCodec;

    public enum QuestionSortType { MOST_VIEWED, MOST_POPULAR, LATEST }
    public enum QuestionScope { ALL, SLIDE }
    private record OffsetCursor(int offset) {}

    public ListResponse list(UUID spaceId, QuestionSortType sort, UUID documentId, UUID categoryId, String cursor, int size, User user, boolean mine) {
        SpaceMember member=requireMember(spaceId,user); if(mine && member.getRole()!=SpaceMemberRole.STUDENT) fail(QuestionErrorCode.STUDENT_ONLY);
        if(documentId!=null) requireDocument(documentId,spaceId);
        int pageSize=size(size), offset=offset(cursor);
        List<Question> filtered=(mine?questions.findAllByDocumentSpaceIdAndStudentIdAndDeletedFalse(spaceId,user.getId()):questions.findAllByDocumentSpaceIdAndDeletedFalse(spaceId)).stream()
            .filter(q->documentId==null||q.getDocument().getId().equals(documentId))
            .filter(q->categoryId==null||mappings.findAllByQuestionId(q.getId()).stream().anyMatch(m->m.getCategory().getId().equals(categoryId)&&!m.getCategory().isDeleted()))
            .sorted(comparator(sort)).toList();
        List<Question> page=filtered.stream().skip(offset).limit(pageSize).toList(); boolean hasNext=offset+page.size()<filtered.size();
        return new ListResponse(page.stream().map(this::listItem).toList(), filtered.size(), hasNext?cursorCodec.encode(new OffsetCursor(offset+page.size())):null, hasNext);
    }

    public Summary summary(UUID spaceId, User user){ SpaceMember m=requireMember(spaceId,user); if(m.getRole()!=SpaceMemberRole.STUDENT) fail(QuestionErrorCode.STUDENT_ONLY);
        long total=questions.countByDocumentSpaceIdAndStudentIdAndDeletedFalse(spaceId,user.getId()); long answered=questions.findAllByDocumentSpaceIdAndStudentIdAndDeletedFalse(spaceId,user.getId()).stream().filter(q->q.getStatus()==QuestionStatus.ANSWERED).count(); return new Summary(total,answered,total-answered); }

    public DocumentListResponse documentQuestions(UUID documentId, QuestionScope scope, UUID slideId, String cursor, int size, User user){
        Document d=getDocument(documentId); requireMember(d.getSpace().getId(),user); if(scope==QuestionScope.SLIDE&&slideId==null) fail(QuestionErrorCode.INVALID_SCOPE);
        if(slideId!=null){ Slide s=getSlide(slideId); if(!s.getDocument().getId().equals(documentId)) fail(QuestionErrorCode.SLIDE_NOT_FOUND); }
        List<Question> all=(scope==QuestionScope.SLIDE?questions.findAllBySlideIdAndDeletedFalse(slideId):questions.findAllByDocumentIdAndDeletedFalse(documentId)).stream().sorted(comparator(QuestionSortType.LATEST)).toList();
        int n=size(size),o=offset(cursor); List<Question> page=all.stream().skip(o).limit(n).toList(); boolean next=o+page.size()<all.size();
        return new DocumentListResponse(page.stream().map(this::documentItem).toList(), next?cursorCodec.encode(new OffsetCursor(o+page.size())):null,next);
    }

    @Transactional public Detail detail(UUID id,User user){ Question q=getQuestion(id); requireMember(q.getDocument().getSpace().getId(),user); q.increaseViewCount();
        return new Detail(q.getId(),q.getTitle(),q.getContent(),doc(q.getDocument()),slide(q.getSlide()),categoryInfo(q),q.getXRatio(),q.getYRatio(),q.getViewCount(),q.getLikeCount(),likes.existsByQuestionIdAndUserId(id,user.getId()),q.getStatus(),
          answers.findAllByQuestionIdAndDeletedFalseOrderByCreatedAtAsc(id).stream().map(this::answerInfo).toList(),comments.findAllByQuestionIdAndDeletedFalseOrderByCreatedAtAsc(id).stream().map(this::commentInfo).toList()); }

    @Transactional public Create createPinned(UUID slideId,QuestionCreateRequest r,User user){ validatePin(r.xRatio(),r.yRatio()); Slide s=getSlide(slideId); requireStudent(s.getDocument().getSpace().getId(),user); Question q=questions.save(Question.createWithPin(s.getDocument(),s,user,r.title().trim(),r.content().trim(),r.xRatio(),r.yRatio())); return new Create(q.getId(),q.getDocument().getId(),s.getId(),q.getTitle(),q.getContent(),q.getXRatio(),q.getYRatio(),List.of(),q.getStatus(),q.getCreatedAt()); }
    @Transactional public SpaceCreate create(UUID spaceId,SpaceQuestionCreateRequest r,User user){ requireStudent(spaceId,user); Document d=requireDocument(r.documentId(),spaceId); Question q=questions.save(Question.create(d,user,r.title().trim(),r.content().trim())); return new SpaceCreate(q.getId(),doc(d),null,q.getTitle(),q.getContent(),List.of(),q.getStatus(),q.getCreatedAt()); }

    public SimilarResponse similar(UUID spaceId,SimilarQuestionRequest r,User user){ requireStudent(spaceId,user); requireDocument(r.documentId(),spaceId); if(r.slideId()!=null){Slide s=getSlide(r.slideId());if(!s.getDocument().getId().equals(r.documentId()))fail(QuestionErrorCode.SLIDE_NOT_FOUND);}
        Set<String> source=tokens(r.title()+" "+r.content()); List<SimilarItem> found=questions.findAllByDocumentIdAndDeletedFalse(r.documentId()).stream().map(q->new SimilarItem(q.getId(),q.getTitle(),q.getContent(),categoryInfo(q),q.getStatus(),q.getLikeCount(),jaccard(source,tokens(q.getTitle()+" "+q.getContent())))).filter(x->x.similarity()>=0.2).sorted(Comparator.comparingDouble(SimilarItem::similarity).reversed()).limit(5).toList(); return new SimilarResponse(found); }

    @Transactional public Delete deleteQuestion(UUID id,User user){ Question q=getQuestion(id); requireProfessor(q.getDocument().getSpace().getId(),user); q.delete(); return new Delete(q.getId(),true,q.getDeletedAt()); }
    @Transactional public AnswerMutation addAnswer(UUID qid,String content,User user){ Question q=getQuestion(qid); requireManager(q.getDocument().getSpace().getId(),user); Answer a=answers.save(Answer.create(q,user,content.trim())); q.markAnswered(); return new AnswerMutation(a.getId(),qid,a.getContent(),a.getCreatedAt(),a.getUpdatedAt(),null); }
    @Transactional public AnswerMutation updateAnswer(UUID id,String content,User user){ Answer a=getAnswer(id); if(!a.getAuthor().getId().equals(user.getId()))fail(QuestionErrorCode.AUTHOR_ONLY);a.updateContent(content.trim());return new AnswerMutation(a.getId(),a.getQuestion().getId(),a.getContent(),a.getCreatedAt(),Instant.now(),null);}
    @Transactional public AnswerMutation deleteAnswer(UUID id,User user){Answer a=getAnswer(id);SpaceMember m=requireMember(a.getQuestion().getDocument().getSpace().getId(),user);if(!a.getAuthor().getId().equals(user.getId())&&m.getRole()!=SpaceMemberRole.PROFESSOR)fail(QuestionErrorCode.AUTHOR_ONLY);a.delete();if(answers.countByQuestionIdAndDeletedFalse(a.getQuestion().getId())==0)a.getQuestion().markPending();return new AnswerMutation(a.getId(),a.getQuestion().getId(),a.getContent(),a.getCreatedAt(),a.getUpdatedAt(),true);}
    @Transactional public CommentMutation addComment(UUID qid,CommentCreateRequest r,User user){Question q=getQuestion(qid);requireManager(q.getDocument().getSpace().getId(),user);QuestionComment parent=r.parentCommentId()==null?null:getComment(r.parentCommentId());if(parent!=null&&!parent.getQuestion().getId().equals(qid))fail(QuestionErrorCode.COMMENT_NOT_FOUND);QuestionComment c=comments.save(QuestionComment.create(q,user,parent,r.content().trim()));return commentMutation(c,null);}
    @Transactional public CommentMutation updateComment(UUID id,String content,User user){QuestionComment c=getComment(id);if(!c.getAuthor().getId().equals(user.getId()))fail(QuestionErrorCode.AUTHOR_ONLY);c.updateContent(content.trim());return commentMutation(c,null);}
    @Transactional public CommentMutation deleteComment(UUID id,User user){QuestionComment c=getComment(id);SpaceMember m=requireMember(c.getQuestion().getDocument().getSpace().getId(),user);if(!c.getAuthor().getId().equals(user.getId())&&m.getRole()!=SpaceMemberRole.PROFESSOR)fail(QuestionErrorCode.AUTHOR_ONLY);c.delete();return commentMutation(c,true);}
    @Transactional public Like like(UUID id,User user){Question q=getQuestionForUpdate(id);requireMember(q.getDocument().getSpace().getId(),user);if(likes.existsByQuestionIdAndUserId(id,user.getId()))fail(QuestionErrorCode.LIKE_ALREADY_EXISTS);likes.save(QuestionLike.create(q,user));q.increaseLikeCount();return new Like(id,true,q.getLikeCount());}
    @Transactional public Like unlike(UUID id,User user){Question q=getQuestionForUpdate(id);requireMember(q.getDocument().getSpace().getId(),user);if(!likes.existsByQuestionIdAndUserId(id,user.getId()))fail(QuestionErrorCode.LIKE_NOT_FOUND);likes.deleteByQuestionIdAndUserId(id,user.getId());q.decreaseLikeCount();return new Like(id,false,q.getLikeCount());}

    public CategoriesResponse categoryList(UUID spaceId,User user){requireMember(spaceId,user);return new CategoriesResponse(documents.findAllBySpaceIdOrderByCreatedAtDescIdDesc(spaceId).stream().map(d->new DocumentCategories(d.getId(),d.getTitle(),categories.findAllByDocumentIdAndDeletedFalse(d.getId()).stream().map(c->new CategoryItem(c.getId(),c.getName(),c.getCreatedBy()==null?"AI":"MANUAL")).toList())).toList());}
    @Transactional public CategoryBatchResponse saveCategories(UUID spaceId,CategoryBatchRequest request,User user){requireManager(spaceId,user);List<CategoryResult> out=new ArrayList<>();for(var op:request.operations()){Document d=requireDocument(op.documentId(),spaceId);QuestionCategory c;switch(op.type()){
      case CREATE->{String name=name(op.name());if(categories.existsByDocumentIdAndNameAndDeletedFalse(d.getId(),name))fail(QuestionErrorCode.CATEGORY_DUPLICATED);c=categories.save(QuestionCategory.createManual(d,name,user));}
      case UPDATE->{c=getCategory(op.categoryId(),d.getId());String name=name(op.name());if(!c.getName().equals(name)&&categories.existsByDocumentIdAndNameAndDeletedFalse(d.getId(),name))fail(QuestionErrorCode.CATEGORY_DUPLICATED);c.updateName(name);}
      case DELETE->{c=getCategory(op.categoryId(),d.getId());c.delete();}
      default->throw new BusinessException(QuestionErrorCode.INVALID_CATEGORY_OPERATION);}
      out.add(new CategoryResult(op.operationId(),op.type().name(),d.getId(),op.tempId(),c.getId(),c.getName(),"SUCCESS"));}return new CategoryBatchResponse(out,Instant.now());}
    public ExportResponse export(UUID spaceId,String format,User user){requireManager(spaceId,user);if(!"csv".equalsIgnoreCase(format))fail(QuestionErrorCode.INVALID_CATEGORY_OPERATION);StringBuilder csv=new StringBuilder("question_id,title,content,status,like_count,view_count\n");questions.findAllByDocumentSpaceIdAndDeletedFalse(spaceId).forEach(q->csv.append(q.getId()).append(',').append(quote(q.getTitle())).append(',').append(quote(q.getContent())).append(',').append(q.getStatus()).append(',').append(q.getLikeCount()).append(',').append(q.getViewCount()).append('\n'));return new ExportResponse("data:text/csv;base64,"+Base64.getEncoder().encodeToString(csv.toString().getBytes(StandardCharsets.UTF_8)));}

    private ListItem listItem(Question q){return new ListItem(q.getId(),q.getTitle(),doc(q.getDocument()),slide(q.getSlide()),categoryInfo(q),q.getCreatedAt(),q.getViewCount(),q.getLikeCount(),q.getStatus());}
    private DocumentListItem documentItem(Question q){return new DocumentListItem(q.getId(),q.getTitle(),q.getContent(),slide(q.getSlide()),categoryInfo(q),q.getXRatio(),q.getYRatio(),q.getLikeCount(),q.getStatus());}
    private DocumentInfo doc(Document d){return new DocumentInfo(d.getId(),d.getTitle());} private SlideInfo slide(Slide s){return s==null?null:new SlideInfo(s.getId(),s.getPageNumber(),s.getThumbnailKey());}
    private List<CategoryInfo> categoryInfo(Question q){return mappings.findAllByQuestionId(q.getId()).stream().filter(m->!m.getCategory().isDeleted()).map(m->new CategoryInfo(m.getCategory().getId(),m.getCategory().getName())).toList();}
    private AuthorInfo author(User u){return new AuthorInfo(u.getId(),u.getName(),u.getProfileUrl());} private AnswerInfo answerInfo(Answer a){return new AnswerInfo(a.getId(),author(a.getAuthor()),a.getContent(),a.getCreatedAt(),a.getUpdatedAt());}
    private CommentInfo commentInfo(QuestionComment c){return new CommentInfo(c.getId(),c.getParentComment()==null?null:c.getParentComment().getId(),author(c.getAuthor()),c.getContent(),c.getCreatedAt(),c.getUpdatedAt());}
    private CommentMutation commentMutation(QuestionComment c,Boolean deleted){return new CommentMutation(c.getId(),c.getQuestion().getId(),c.getParentComment()==null?null:c.getParentComment().getId(),c.getContent(),c.getCreatedAt(),deleted==null?Instant.now():c.getUpdatedAt(),deleted);}
    private Comparator<Question> comparator(QuestionSortType t){Comparator<Question> tie=Comparator.comparing(Question::getCreatedAt).thenComparing(Question::getId).reversed();return switch(t==null?QuestionSortType.LATEST:t){case MOST_VIEWED->Comparator.comparing(Question::getViewCount).reversed().thenComparing(tie);case MOST_POPULAR->Comparator.comparing(Question::getLikeCount).reversed().thenComparing(tie);case LATEST->tie;};}
    private int size(int n){return n<=0?DEFAULT_SIZE:Math.min(n,MAX_SIZE);}private int offset(String c){OffsetCursor x=cursorCodec.decodeOrNull(c,OffsetCursor.class);return x==null?0:Math.max(0,x.offset());}
    private SpaceMember requireMember(UUID sid,User u){return members.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(sid,u.getId(),SpaceMemberStatus.APPROVED).orElseThrow(()->new BusinessException(QuestionErrorCode.SPACE_MEMBER_REQUIRED));}
    private SpaceMember requireStudent(UUID sid,User u){SpaceMember m=requireMember(sid,u);if(m.getRole()!=SpaceMemberRole.STUDENT)fail(QuestionErrorCode.STUDENT_ONLY);return m;}
    private SpaceMember requireManager(UUID sid,User u){SpaceMember m=requireMember(sid,u);if(m.getRole()!=SpaceMemberRole.PROFESSOR&&!(m.getRole()==SpaceMemberRole.ASSISTANT&&permissions.existsBySpaceMemberIdAndPermission(m.getId(),PermissionType.QUESTION_MANAGE)))fail(QuestionErrorCode.QUESTION_MANAGE_FORBIDDEN);return m;}
    private void requireProfessor(UUID sid,User u){if(requireMember(sid,u).getRole()!=SpaceMemberRole.PROFESSOR)fail(QuestionErrorCode.QUESTION_MANAGE_FORBIDDEN);}
    private Question getQuestion(UUID id){return questions.findById(id).filter(q->!q.isDeleted()).orElseThrow(()->new BusinessException(QuestionErrorCode.QUESTION_NOT_FOUND));}private Question getQuestionForUpdate(UUID id){return questions.findQuestionById(id).filter(q->!q.isDeleted()).orElseThrow(()->new BusinessException(QuestionErrorCode.QUESTION_NOT_FOUND));}private Answer getAnswer(UUID id){return answers.findById(id).filter(a->!a.isDeleted()).orElseThrow(()->new BusinessException(QuestionErrorCode.ANSWER_NOT_FOUND));}private QuestionComment getComment(UUID id){return comments.findById(id).filter(c->!c.isDeleted()).orElseThrow(()->new BusinessException(QuestionErrorCode.COMMENT_NOT_FOUND));}
    private Document getDocument(UUID id){return documents.findById(id).orElseThrow(()->new BusinessException(QuestionErrorCode.DOCUMENT_NOT_FOUND));}private Document requireDocument(UUID id,UUID sid){Document d=getDocument(id);if(!d.getSpace().getId().equals(sid))fail(QuestionErrorCode.DOCUMENT_NOT_FOUND);return d;}private Slide getSlide(UUID id){return slides.findById(id).orElseThrow(()->new BusinessException(QuestionErrorCode.SLIDE_NOT_FOUND));}
    private QuestionCategory getCategory(UUID id,UUID did){if(id==null)fail(QuestionErrorCode.INVALID_CATEGORY_OPERATION);return categories.findById(id).filter(c->!c.isDeleted()&&c.getDocument().getId().equals(did)).orElseThrow(()->new BusinessException(QuestionErrorCode.CATEGORY_NOT_FOUND));}
    private String name(String s){if(s==null||s.isBlank())fail(QuestionErrorCode.INVALID_CATEGORY_OPERATION);return s.trim();}private void validatePin(double x,double y){if(x<0||x>1||y<0||y>1)fail(QuestionErrorCode.INVALID_PIN);}private static void fail(QuestionErrorCode e){throw new BusinessException(e);}
    private Set<String> tokens(String s){Set<String> out=new HashSet<>();for(String x:s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))if(x.length()>1)out.add(x);return out;}private double jaccard(Set<String>a,Set<String>b){if(a.isEmpty()&&b.isEmpty())return 0;Set<String> i=new HashSet<>(a);i.retainAll(b);Set<String> u=new HashSet<>(a);u.addAll(b);return (double)i.size()/u.size();}private String quote(String s){return "\""+s.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\"";}
}
