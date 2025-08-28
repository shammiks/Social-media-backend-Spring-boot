package com.example.DPMHC_backend.dto;

import com.example.DPMHC_backend.model.Chat;
import com.example.DPMHC_backend.model.Message;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaUploadDTO {

    private Long id;
    private String originalFilename;
    private String fileUrl;
    private String thumbnailUrl;
    private String fileType;
    private Long fileSize;
    private String mimeType;
    private Integer width;
    private Integer height;
    private Long duration;
    private String cloudinaryPublicId;

    // File size formatted for display
    private String fileSizeFormatted;
}
