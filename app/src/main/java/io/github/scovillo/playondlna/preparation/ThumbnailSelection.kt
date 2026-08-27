package io.github.scovillo.playondlna.preparation

import org.schabi.newpipe.extractor.Image

fun List<Image>.bestThumbnailUrl(): String? =
    maxWithOrNull(
        compareBy<Image> { image ->
            when (image.estimatedResolutionLevel) {
                Image.ResolutionLevel.HIGH -> 3
                Image.ResolutionLevel.MEDIUM -> 2
                Image.ResolutionLevel.LOW -> 1
                Image.ResolutionLevel.UNKNOWN -> 0
            }
        }.thenBy { image ->
            image.width.coerceAtLeast(0).toLong() * image.height.coerceAtLeast(0).toLong()
        },
    )?.url
