package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.model.Post;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealTimeService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void broadcastNewPost(Post post) {
        messagingTemplate.convertAndSend("/topic/new-post", post);
    }
}
