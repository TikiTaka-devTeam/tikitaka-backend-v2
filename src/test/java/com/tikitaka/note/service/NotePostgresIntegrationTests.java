package com.tikitaka.note.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import com.tikitaka.global.security.JwtProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.tikitaka.document.entity.Slide;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.note.dto.request.FixerRequest;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.s3.S3Service;
import com.tikitaka.global.sms.SmsSender;
import com.tikitaka.note.dto.request.StrokeSyncRequest;
import com.tikitaka.note.dto.request.StrokeSyncRequest.*;
import com.tikitaka.note.dto.response.StrokeSyncResponse;
import com.tikitaka.note.entity.StrokeTool;
import com.tikitaka.note.exception.NoteErrorCode;
import com.tikitaka.user.entity.User;
import com.tikitaka.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "solapi.api-key=test", "solapi.api-secret=test", "solapi.sender-number=01000000000"
})
@ActiveProfiles("test")
@Testcontainers
class NotePostgresIntegrationTests {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg16").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @MockitoBean SmsSender sms;
    @MockitoBean S3Service s3;
    @Autowired NoteService notes;

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired UserRepository users;
    @Autowired SlideRepository slides;
    @Autowired WebApplicationContext context;
    @Autowired JwtProvider jwt;
    @Autowired ObjectMapper mapper;
    MockMvc mvc;
    static final AtomicInteger sequence = new AtomicInteger();
    UUID spaceId, documentId, slideId;
    User professor, student;

    @BeforeEach void seed() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        professor = user("PROFESSOR"); student = user("STUDENT");
        spaceId=UUID.randomUUID(); documentId=UUID.randomUUID(); slideId=UUID.randomUUID();
        jdbc.update("INSERT INTO spaces(id,professor_id,space_name,year,semester,space_code) VALUES (?,?,?,2026,'2',?)",
                spaceId,professor.getId(),"필기 테스트",String.format("%08d",sequence.incrementAndGet()));
        member(professor,"PROFESSOR"); member(student,"STUDENT");
        jdbc.update("INSERT INTO documents(id,space_id,title,thumbnail_key,pdf_key,page_count) VALUES (?,?,?,'thumb','pdf',1)",documentId,spaceId,"자료");
        jdbc.update("INSERT INTO slides(id,document_id,page_number,thumbnail_key) VALUES (?,?,1,'slide')",slideId,documentId);
    }
    User user(String role) {
        UUID id=UUID.randomUUID(); int number=sequence.incrementAndGet();
        jdbc.update("INSERT INTO users(id,email,name,account_type,phone_number,univ,major,member_id_number) VALUES (?,?,?,?,?,?,?,?)",
                id,id+"@test.example","테스트",role,String.format("010%08d",number),"대학","학과","번호");
        return users.findById(id).orElseThrow();
    }
    UUID member(User user,String role) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO space_members(id,space_id,user_id,color_key,role,status) VALUES (?,?,?,'COLOR_1',?,'APPROVED')",id,spaceId,user.getId(),role);
        return id;
    }
    Operation create() {
        return new Operation(UUID.randomUUID(),Type.CREATE,new Stroke(UUID.randomUUID(),StrokeTool.PEN,
                List.of(new Point(0.1,0.2),new Point(0.2,0.3)),"#000000",2.0,1.0,0),null);
    }
    StrokeSyncRequest request(int version,Operation... operations) {
        return new StrokeSyncRequest(version,List.of(operations));
    }
    @Test void persistsJsonbAndReturnsOriginalMappingOnStaleRetry() {
        Operation operation=create();
        var first=notes.syncPrivate(slideId,request(0,operation),student);
        var retry=notes.syncPrivate(slideId,request(0,operation),student);
        assertThat(retry.appliedCount()).isZero();
        assertThat(retry.createdStrokes()).isEqualTo(first.createdStrokes());
        assertThat(retry.version()).isEqualTo(1);
        assertThat(notes.getPrivate(slideId,student).strokes()).hasSize(1);
        assertThat(count("private_stroke_operations")).isEqualTo(1);
    }
    @Test void failingBatchRollsBackStrokeOperationAndLayerCreation() {
        Operation badDelete=new Operation(UUID.randomUUID(),Type.DELETE,null,UUID.randomUUID());
        assertThatThrownBy(()->notes.syncPrivate(slideId,request(0,create(),badDelete),student))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(NoteErrorCode.NOTE_STROKE_NOT_FOUND));
        assertThat(count("private_strokes")).isZero();
        assertThat(count("private_stroke_operations")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM private_layers WHERE slide_id=?",Integer.class,slideId)).isZero();
        assertThat(notes.getPrivate(slideId,student).version()).isZero();
    }
    @Test void concurrentInitialPrivateSyncHasOneSuccessAndOneConflict() throws Exception {
        assertConcurrentConflict(()->notes.syncPrivate(slideId,request(0,create()),student),
                ()->notes.syncPrivate(slideId,request(0,create()),student));
        assertThat(notes.getPrivate(slideId,student).version()).isEqualTo(1);
        assertThat(notes.getPrivate(slideId,student).strokes()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM private_layers WHERE slide_id=?",Integer.class,slideId)).isEqualTo(1);
    }
    @Test void professorAndAssistantCompeteForOneSharedLayer() throws Exception {
        User assistant=user("STUDENT"); UUID assistantMember=member(assistant,"ASSISTANT");
        jdbc.update("INSERT INTO space_member_permissions(space_member_id,permission) VALUES (?,'LECTURE_MATERIAL_MANAGE')",assistantMember);
        assertConcurrentConflict(()->notes.syncShared(slideId,request(0,create()),professor),
                ()->notes.syncShared(slideId,request(0,create()),assistant));
        assertThat(notes.getShared(slideId,student).strokes()).hasSize(1);
        assertThat(count("shared_stroke_operations")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM private_layers WHERE slide_id=?",Integer.class,slideId)).isZero();
    }
    @Test void readTransactionDoesNotBlockPrivateSync() throws Exception {
        notes.syncPrivate(slideId, request(0, create()), student);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch readComplete = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        try {
            Future<?> read = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                notes.getPrivate(slideId, student);
                readComplete.countDown();
                try {
                    if (!releaseRead.await(5, TimeUnit.SECONDS)) throw new AssertionError("read was not released");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }));
            assertThat(readComplete.await(5, TimeUnit.SECONDS)).isTrue();

            StrokeSyncResponse sync = pool.submit(() -> notes.syncPrivate(slideId, request(1, create()), student))
                    .get(3, TimeUnit.SECONDS);

            assertThat(sync.version()).isEqualTo(2);
            releaseRead.countDown();
            read.get(5, TimeUnit.SECONDS);
        } finally {
            releaseRead.countDown();
            pool.shutdownNow();
        }
    }
    @Test void privateDeletionDoesNotChangeSharedLayerAndRepeatedDeleteIsNoOp() {
        var shared=notes.syncShared(slideId,request(0,create()),professor);
        var personal=notes.syncPrivate(slideId,request(0,create()),student);
        UUID strokeId=personal.createdStrokes().get(0).strokeId();
        notes.syncPrivate(slideId,request(1,new Operation(UUID.randomUUID(),Type.DELETE,null,strokeId)),student);
        var repeated=notes.syncPrivate(slideId,request(2,new Operation(UUID.randomUUID(),Type.DELETE,null,strokeId)),student);
        assertThat(repeated.version()).isEqualTo(2); assertThat(repeated.appliedCount()).isZero();
        assertThat(count("private_stroke_operations")).isEqualTo(2);
        assertThat(notes.getPrivate(slideId,student).strokes()).isEmpty();
        var teacherLayer=notes.getShared(slideId,student);
        assertThat(teacherLayer.version()).isEqualTo(shared.version()); assertThat(teacherLayer.strokes()).hasSize(1);
    }
    @Test void crossLayerDeleteAndOperationContentChangeAreRejected() {
        Operation operation=create();
        var first=notes.syncPrivate(slideId,request(0,operation),student);
        User other=user("STUDENT"); member(other,"STUDENT");
        UUID strokeId=first.createdStrokes().get(0).strokeId();
        assertThatThrownBy(()->notes.syncPrivate(slideId,request(0,new Operation(UUID.randomUUID(),Type.DELETE,null,strokeId)),other))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(NoteErrorCode.NOTE_STROKE_NOT_FOUND));
        assertThatThrownBy(()->notes.syncPrivate(slideId,request(1,new Operation(operation.clientOperationId(),Type.DELETE,null,strokeId)),student))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(NoteErrorCode.NOTE_OPERATION_ID_CONFLICT));
        assertThat(notes.getPrivate(slideId,student).strokes()).hasSize(1);
    }
    @Test void clientStrokeIdCannotBeReusedByAnotherOperation() {
        Operation operation=create(); notes.syncPrivate(slideId,request(0,operation),student);
        assertThatThrownBy(()->notes.syncPrivate(slideId,request(1,new Operation(UUID.randomUUID(),Type.CREATE,operation.stroke(),null)),student))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(NoteErrorCode.NOTE_CLIENT_STROKE_ID_CONFLICT));
        assertThat(notes.getPrivate(slideId,student).version()).isEqualTo(1);
    }
    @Test void deletingDocumentCascadesBothLayersAndOperationRecords() {
        notes.syncPrivate(slideId,request(0,create()),student);
        notes.syncShared(slideId,request(0,create()),professor);
        jdbc.update("DELETE FROM documents WHERE id=?",documentId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM private_stroke_operations",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared_stroke_operations",Integer.class)).isZero();
    }
    @Test void fixerCompletionRemainsIdempotentInDatabase() {
        var created=notes.createFixer(slideId,new FixerRequest(0.1,0.2," 수정 필요 "),professor);
        notes.checkFixer(created.fixerId(),professor);
        var first=jdbc.queryForObject("SELECT checked_at FROM fixers WHERE id=?",java.sql.Timestamp.class,created.fixerId());
        notes.checkFixer(created.fixerId(),professor);
        assertThat(jdbc.queryForObject("SELECT checked_at FROM fixers WHERE id=?",java.sql.Timestamp.class,created.fixerId())).isEqualTo(first);
        assertThat(notes.getFixers(slideId,professor).get(0).content()).isEqualTo("수정 필요");
    }
    String token(User user) { return "Bearer " + jwt.issue(user.getId()).accessToken(); }

    @Test void httpPrivateCreateReadAndRetryFollowSnakeCaseContract() throws Exception {
        String syncPath="/api/v1/slides/"+slideId+"/private-strokes/sync";
        String body=mapper.writeValueAsString(request(0,create()));
        mvc.perform(post(syncPath).header("Authorization",token(student)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.applied_count").value(1))
                .andExpect(jsonPath("$.created_strokes[0].client_stroke_id").exists())
                .andExpect(jsonPath("$.created_strokes[0].stroke_id").exists());
        mvc.perform(post(syncPath).header("Authorization",token(student)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applied_count").value(0));
        mvc.perform(get("/api/v1/slides/"+slideId+"/private-strokes").header("Authorization",token(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slide_id").value(slideId.toString()))
                .andExpect(jsonPath("$.strokes[0].points[0].x_ratio").value(0.1))
                .andExpect(jsonPath("$.strokes[0].is_deleted").value(false));
    }
    @Test void httpSharedAndFixerLifecycleUseProfessorLayerAndPrivateMemo() throws Exception {
        mvc.perform(post("/api/v1/slides/"+slideId+"/shared-strokes/sync").header("Authorization",token(professor))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request(0,create()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/slides/"+slideId+"/shared-strokes").header("Authorization",token(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.strokes.length()").value(1));
        String created=mvc.perform(post("/api/v1/slides/"+slideId+"/fixers").header("Authorization",token(professor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"x_ratio\":0.1,\"y_ratio\":0.2,\"content\":\"수정 필요\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.is_checked").value(false))
                .andExpect(jsonPath("$.slide_id").value(slideId.toString())).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String id=mapper.readTree(created).get("fixer_id").asText();
        mvc.perform(patch("/api/v1/fixers/"+id+"/check").header("Authorization",token(professor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.is_checked").value(true));
        mvc.perform(get("/api/v1/slides/"+slideId+"/fixers").header("Authorization",token(professor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].is_checked").value(true));
    }
    @Test void httpUnauthenticatedAndWrongRoleRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/slides/"+slideId+"/private-strokes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/slides/"+slideId+"/private-strokes").header("Authorization",token(professor)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOTE_ACCESS_DENIED"));
        mvc.perform(post("/api/v1/slides/"+slideId+"/shared-strokes/sync").header("Authorization",token(student))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request(0,create()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/slides/"+slideId+"/fixers").header("Authorization",token(student)))
                .andExpect(status().isForbidden());
    }
    @Test void httpEmptyBatchAndStaleVersionReturnSpecifiedErrors() throws Exception {
        String path="/api/v1/slides/"+slideId+"/private-strokes/sync";
        mvc.perform(post(path).header("Authorization",token(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"base_version\":0,\"operations\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
        notes.syncPrivate(slideId,request(0,create()),student);
        mvc.perform(post(path).header("Authorization",token(student)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request(0,create()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTE_VERSION_CONFLICT"));
    }
    int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " s JOIN "
                + (table.startsWith("private")?"private_layers":"shared_layers") + " l ON l.id=s.layer_id WHERE l.slide_id=?",Integer.class,slideId);
    }
    void assertConcurrentConflict(Callable<StrokeSyncResponse> one,Callable<StrokeSyncResponse> two) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<Object>> futures=new ArrayList<>();
            for (var action:List.of(one,two)) futures.add(pool.submit(()->{
                start.await(); try { return action.call(); } catch (BusinessException e) { return e.getErrorCode(); }
            }));
            start.countDown(); List<Object> outcomes=new ArrayList<>();
            for(var future:futures) outcomes.add(future.get(30,TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(StrokeSyncResponse.class::isInstance).count()).isEqualTo(1);
            assertThat(outcomes).contains(NoteErrorCode.NOTE_VERSION_CONFLICT);
        } finally { pool.shutdownNow(); }
    }
    @AfterEach void cleanup() { jdbc.update("DELETE FROM spaces WHERE id=?",spaceId); }
}
