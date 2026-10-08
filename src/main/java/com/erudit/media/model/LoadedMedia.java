package com.erudit.media.model;

import org.springframework.core.io.Resource;

public record LoadedMedia(MediaAsset metadata, Resource resource) {
}
