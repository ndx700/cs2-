package com.ali.cs2utility.presentation

import com.ali.cs2utility.domain.*

/** UI-independent state. Repository and persistence are owned at the composition boundary. */
class ExplorerState(val maps: List<MapDefinition>) {
    lateinit var map: MapDefinition
    var lineups: List<Lineup> = emptyList()
    var favorites: Set<String> = emptySet()
    var filter = Filter()
    var selectedId: String? = null
    var selectedGroupId: String? = null
    val groups get() = TargetGroups.from(visible)
    val selectedGroup get() = groups.firstOrNull { it.id==selectedGroupId }
    val visible get() = LineupFilter.apply(lineups, filter, favorites)
    val selected get() = lineups.firstOrNull { it.id == selectedId }
    fun select(id: String?) {
        selectedId = id?.takeIf { candidate -> lineups.any { it.id == candidate } }
        selected?.let { selectedGroupId=it.groupId.ifBlank { it.id } }
    }
    fun reconcileSelection() {
        if (visible.none { it.id == selectedId }) selectedId = null
        if(groups.none { it.id==selectedGroupId }) selectedGroupId=null
    }
    fun toggleFavorite(id: String) { favorites = if (id in favorites) favorites - id else favorites + id }
}
