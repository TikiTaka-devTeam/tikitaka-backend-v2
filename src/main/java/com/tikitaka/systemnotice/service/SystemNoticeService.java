package com.tikitaka.systemnotice.service;

import java.time.Instant; import java.util.List; import java.util.UUID;
import org.springframework.data.domain.PageRequest; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import com.tikitaka.global.common.cursor.CursorCodec; import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.systemnotice.dto.response.*; import com.tikitaka.systemnotice.entity.*; import com.tikitaka.systemnotice.exception.SystemNoticeErrorCode; import com.tikitaka.systemnotice.repository.*; import com.tikitaka.user.entity.User;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class SystemNoticeService {
 private static final int DEFAULT_SIZE=20, MAX_SIZE=100, PREVIEW_LENGTH=50;
 private final SystemNoticeRepository systemNoticeRepository; private final SystemNoticeReadRepository readRepository; private final CursorCodec cursorCodec;
 public SystemNoticeListResponse getSystemNotices(String cursor,int size,User currentUser){
  int pageSize=size<=0?DEFAULT_SIZE:Math.min(size,MAX_SIZE); SystemNoticeCursor decoded=cursorCodec.decodeOrNull(cursor,SystemNoticeCursor.class);
  List<SystemNotice> fetched=systemNoticeRepository.findPage(decoded==null?null:decoded.createdAt(),decoded==null?null:decoded.id(),PageRequest.of(0,pageSize+1));
  boolean hasNext=fetched.size()>pageSize; List<SystemNotice> page=hasNext?fetched.subList(0,pageSize):fetched;
  List<SystemNoticeListItemResponse> items=page.stream().map(n->new SystemNoticeListItemResponse(n.getId(),n.getTitle(),preview(n.getContent()),n.isImportant(),readRepository.existsBySystemNoticeIdAndUserId(n.getId(),currentUser.getId()),n.getCreatedAt())).toList();
  String nextCursor=null; if(hasNext&&!page.isEmpty()){SystemNotice last=page.get(page.size()-1);nextCursor=cursorCodec.encode(new SystemNoticeCursor(last.getCreatedAt(),last.getId()));}
  return new SystemNoticeListResponse(systemNoticeRepository.count(),readRepository.countUnread(currentUser.getId()),items,nextCursor,hasNext);
 }
 @Transactional public SystemNoticeDetailResponse getSystemNotice(UUID id,User currentUser){
  SystemNotice notice=systemNoticeRepository.findById(id).orElseThrow(()->new BusinessException(SystemNoticeErrorCode.SYSTEM_NOTICE_NOT_FOUND));
  SystemNoticeRead read=readRepository.findBySystemNoticeIdAndUserId(id,currentUser.getId()).orElseGet(()->readRepository.save(SystemNoticeRead.create(notice,currentUser)));
  return new SystemNoticeDetailResponse(notice.getId(),notice.getTitle(),notice.getContent(),notice.isImportant(),true,read.getReadAt(),notice.getCreatedAt());
 }
 private String preview(String content){String normalized=content.replaceAll("\\s+"," ").trim();return normalized.length()<=PREVIEW_LENGTH?normalized:normalized.substring(0,PREVIEW_LENGTH)+"...";}
 public record SystemNoticeCursor(Instant createdAt,UUID id){}
}
