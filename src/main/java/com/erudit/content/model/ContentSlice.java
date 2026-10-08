package com.erudit.content.model;

import java.util.List;

public record ContentSlice(List<Content> items, int page, int size, boolean hasMore) {
}
