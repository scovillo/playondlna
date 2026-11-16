package io.github.scovillo.playondlna.model

class Playlist(
    val id: String,
    val name: String,
    val items: List<LibraryItem>,
) {
    val size: Int
        get() = items.size

    val isEmpty: Boolean
        get() = items.isEmpty()

    val isNotEmpty: Boolean
        get() = items.isNotEmpty()

    operator fun get(index: Int): LibraryItem {
        return items[index]
    }

    companion object {
        fun empty(): Playlist {
            return Playlist("", "", emptyList())
        }
    }

    override fun toString(): String {
        return "Playlist(id=$id, name=$name, size=$size)"
    }
}
