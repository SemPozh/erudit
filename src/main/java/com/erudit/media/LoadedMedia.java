package com.erudit.media;

import org.springframework.core.io.Resource;

public record LoadedMedia(MediaAsset metadata, Resource resource) {
}
