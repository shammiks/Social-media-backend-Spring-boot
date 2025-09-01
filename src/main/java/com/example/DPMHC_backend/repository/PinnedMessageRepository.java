package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.PinnedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PinnedMessageRepository extends JpaRepository<PinnedMessage, Long> {
    List<PinnedMessage> findByUserIdAndMessage_Chat_Id(Long userId, Long chatId);
    PinnedMessage findByUserIdAndMessageId(Long userId, Long messageId);
    void deleteByUserIdAndMessageId(Long userId, Long messageId);
}
