package com.example.gemtronicring.p4e7a91c3.domain.usecase

import com.example.gemtronicring.p4e7a91c3.domain.model.MenuCursor
import com.example.gemtronicring.p4e7a91c3.domain.model.MenuItem
import com.example.gemtronicring.p4e7a91c3.domain.model.RingGesture
import com.example.gemtronicring.p4e7a91c3.domain.model.RingMenu

/** Moves through the ring's menu the way the website's ring simulator does. */
class NavigateMenuUseCase {
    fun move(menu: RingMenu, cursor: MenuCursor, gesture: RingGesture): MenuCursor {
        val items = menu.levels[cursor.level].orEmpty()
        if (items.isEmpty()) return cursor
        return when (gesture) {
            RingGesture.Next -> cursor.copy(index = (cursor.index + 1) % items.size)
            RingGesture.Previous ->
                cursor.copy(index = (cursor.index - 1 + items.size) % items.size)
            RingGesture.Confirm -> open(items[cursor.index], cursor)
            RingGesture.Back -> back(cursor)
        }
    }

    fun frame(menu: RingMenu, cursor: MenuCursor): String? =
        menu.levels[cursor.level]?.getOrNull(cursor.index)?.frame

    private fun open(item: MenuItem, cursor: MenuCursor): MenuCursor {
        val target = item.opens ?: return cursor
        return MenuCursor(target, 0, cursor.trail + (cursor.level to cursor.index))
    }

    private fun back(cursor: MenuCursor): MenuCursor {
        val (level, index) = cursor.trail.lastOrNull() ?: return cursor
        return MenuCursor(level, index, cursor.trail.dropLast(1))
    }
}
