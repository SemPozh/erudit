package com.erudit.friend.dto;

import com.erudit.friend.model.FriendRecord;

import java.util.List;

public record FriendPage(List<FriendRecord> items, long total, int page, int size) {}
