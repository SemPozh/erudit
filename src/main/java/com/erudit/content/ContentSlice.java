package com.erudit.content;

import java.util.List;

public record ContentSlice(List<Content> items, int page, int size, boolean hasMore) {
}
