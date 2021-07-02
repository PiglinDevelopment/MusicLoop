package dev.piglin.musicloop;

import java.util.List;

public record Loop(String name, List<Track> tracks, boolean isShuffle) {
}
