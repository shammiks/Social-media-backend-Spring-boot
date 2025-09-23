package com.example.DPMHC_backend.dto.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Cache-friendly wrapper for Spring Data Page objects
 * Solves Redis serialization issues with Page interface
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageCacheWrapper<T> {
    
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    
    /**
     * Create wrapper from existing Page
     */
    public static <T> PageCacheWrapper<T> of(Page<T> page) {
        return new PageCacheWrapper<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast()
        );
    }
    
    /**
     * Convert back to Page object
     */
    public Page<T> toPage(Pageable pageable) {
        return new PageImpl<>(content, pageable, totalElements);
    }
    
    /**
     * Convert back to Page with custom pageable
     */
    public Page<T> toPage() {
        return new PageImpl<>(content, 
            org.springframework.data.domain.PageRequest.of(pageNumber, pageSize), 
            totalElements);
    }
}