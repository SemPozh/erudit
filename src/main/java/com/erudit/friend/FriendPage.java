package com.erudit.friend;

import java.util.List;

public record FriendPage(List<FriendRecord> items, long total, int page, int size) {}
